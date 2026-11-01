/*
 * bfeext - BFE's decoder dispatcher for FreeArc "external compressor" layers.
 *
 * unarc runs it from a per-job arc.ini as
 *     <nativeLibraryDir>/libbfeext.so <layer name> <packed file> <output file>
 * inside unarc's temporary folder. The layer is recognised by the header of its own data, not by
 * the name the archive gives it (archives name the same tool differently), and handed to the bundled
 * decoder that can restore it. Lines meant for BFE's UI start with "BFE-EXT:" (on stderr).
 *
 * FreeArc may put one marker byte in front of an external layer ("header=1", its default): 1 = the
 * layer ran, 0 = the data was stored as is. The per-job arc.ini says header=0, so the marker (when
 * the archive has one) reaches this program and is handled here.
 *
 * Exit codes: 0 ok, 2 decoder not included, 3 unknown layer, 4 I/O error, else the decoder's own.
 *
 * Part of BFE (Banners File Explorer), GPL-3.0-or-later.
 */
#define _FILE_OFFSET_BITS 64
#include <errno.h>
#include <fcntl.h>
#include <limits.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <unistd.h>

#define BUF_SIZE (4 << 20)

static const char *layer = "?";

static void msg(const char *fmt, const char *a, const char *b)
{
    fflush(stdout);
    fprintf(stderr, "BFE-EXT: ");
    fprintf(stderr, fmt, a ? a : "", b ? b : "");
    fputc('\n', stderr);
}

static int io_fail(const char *what, const char *path)
{
    char e[256];
    snprintf(e, sizeof e, "%s (%s)", path, strerror(errno));
    msg("layer '%s': I/O error: %s", layer, e);
    (void)what;
    return 4;
}

/* Copies `in` from byte `skip` on into `out`. */
static int copy_from(const char *in, off_t skip, const char *out)
{
    int fi = open(in, O_RDONLY), fo;
    if (fi < 0) return io_fail("open", in);
    fo = open(out, O_WRONLY | O_CREAT | O_TRUNC, 0644);
    if (fo < 0) { close(fi); return io_fail("create", out); }
    char *buf = malloc(BUF_SIZE);
    if (!buf) { close(fi); close(fo); return 4; }
    if (lseek(fi, skip, SEEK_SET) < 0) { free(buf); close(fi); close(fo); return io_fail("seek", in); }
    for (;;) {
        ssize_t n = read(fi, buf, BUF_SIZE);
        if (n < 0) { free(buf); close(fi); close(fo); return io_fail("read", in); }
        if (n == 0) break;
        for (ssize_t w = 0; w < n; ) {
            ssize_t k = write(fo, buf + w, (size_t)(n - w));
            if (k <= 0) { free(buf); close(fi); close(fo); return io_fail("write", out); }
            w += k;
        }
    }
    free(buf);
    close(fi);
    if (close(fo) != 0) return io_fail("close", out);
    return 0;
}

/* Drops the first byte of `path` in place (no second copy of a possibly huge file). */
static int drop_first_byte(const char *path)
{
    int fd = open(path, O_RDWR);
    if (fd < 0) return io_fail("open", path);
    struct stat st;
    if (fstat(fd, &st) != 0) { close(fd); return io_fail("stat", path); }
    char *buf = malloc(BUF_SIZE);
    if (!buf) { close(fd); return 4; }
    off_t rd = 1, wr = 0;
    while (rd < st.st_size) {
        ssize_t n = pread(fd, buf, BUF_SIZE, rd);
        if (n <= 0) { free(buf); close(fd); return io_fail("read", path); }
        for (ssize_t w = 0; w < n; ) {
            ssize_t k = pwrite(fd, buf + w, (size_t)(n - w), wr + w);
            if (k <= 0) { free(buf); close(fd); return io_fail("write", path); }
            w += k;
        }
        rd += n;
        wr += n;
    }
    free(buf);
    if (ftruncate(fd, st.st_size - 1) != 0) { close(fd); return io_fail("truncate", path); }
    close(fd);
    return 0;
}

/* Folder this program was started from (= the app's nativeLibraryDir). */
static void self_dir(char *out, size_t size)
{
    ssize_t n = readlink("/proc/self/exe", out, size - 1);
    if (n <= 0) { snprintf(out, size, "."); return; }
    out[n] = '\0';
    char *slash = strrchr(out, '/');
    if (slash) *slash = '\0';
}

static int run(char *const argv[])
{
    fflush(stdout);
    pid_t pid = fork();
    if (pid < 0) return io_fail("fork", argv[0]);
    if (pid == 0) {
        execv(argv[0], argv);
        _exit(127);
    }
    int status = 0;
    while (waitpid(pid, &status, 0) < 0) {
        if (errno != EINTR) return io_fail("wait", argv[0]);
    }
    if (WIFEXITED(status)) return WEXITSTATUS(status);
    return 128 + (WIFSIGNALED(status) ? WTERMSIG(status) : 0);
}

static int has_magic(const unsigned char *b, size_t n, size_t at, const char *magic)
{
    size_t m = strlen(magic);
    return n >= at + m && memcmp(b + at, magic, m) == 0;
}

int main(int argc, char **argv)
{
    if (argc != 4) {
        fprintf(stderr, "usage: %s <layer name> <packed file> <output file>\n", argv[0]);
        return 3;
    }
    layer = argv[1];
    const char *in = argv[2], *out = argv[3];

    unsigned char head[16];
    size_t n = 0;
    {
        int fd = open(in, O_RDONLY);
        if (fd < 0) return io_fail("open", in);
        ssize_t k = read(fd, head, sizeof head);
        close(fd);
        if (k < 0) return io_fail("read", in);
        n = (size_t)k;
    }

    /* Where the layer's own data starts: 0 = no marker byte, 1 = FreeArc's "ran" marker. */
    size_t at;
    if (has_magic(head, n, 0, "PCF") || has_magic(head, n, 0, "SREP")) at = 0;
    else if (n > 0 && head[0] == 1 && (has_magic(head, n, 1, "PCF") || has_magic(head, n, 1, "SREP"))) at = 1;
    else if (n > 0 && head[0] == 0) {
        /* FreeArc kept this block as is (the layer didn't shrink it): the rest is the output. */
        msg("layer '%s': stored without the layer", layer, NULL);
        return copy_from(in, 1, out);
    } else {
        msg("layer '%s': unknown format, BFE has no decoder for it", layer, NULL);
        return 3;
    }

    if (has_magic(head, n, at, "SREP")) {
        msg("layer '%s' is SREP (SuperREP): its decoder is not included in BFE", layer, NULL);
        return 2;
    }

    /* precomp: "PCF" + major, minor, minor2. A PCF file restores only with the exact version. */
    if (n < at + 6) { msg("layer '%s': precomp header too short", layer, NULL); return 3; }
    int maj = head[at + 3], min = head[at + 4], min2 = head[at + 5];
    char ver[32], dir[PATH_MAX], bin[PATH_MAX + 64], oarg[PATH_MAX + 8];
    snprintf(ver, sizeof ver, "%d.%d.%d", maj, min, min2);
    self_dir(dir, sizeof dir);
    snprintf(bin, sizeof bin, "%s/libprecomp%d%d%d.so", dir, maj, min, min2);
    if (access(bin, X_OK) != 0) {
        msg("layer '%s' needs precomp %s, which is not included in BFE", layer, ver);
        return 2;
    }
    if (at == 1) {
        int r = drop_first_byte(in);
        if (r) return r;
    }
    unlink(out);
    snprintf(oarg, sizeof oarg, "-o%s", out);
    msg("layer '%s': restoring with precomp %s", layer, ver);
    char *pargv[] = { bin, "-r", oarg, (char *)in, NULL };
    int rc = run(pargv);
    if (rc != 0) {
        char code[16];
        snprintf(code, sizeof code, "%d", rc);
        msg("layer '%s': precomp failed (exit %s)", layer, code);
        return rc;
    }
    if (access(out, F_OK) != 0) {
        msg("layer '%s': precomp wrote no output", layer, NULL);
        return 4;
    }
    return 0;
}
