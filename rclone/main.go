// BFE's trimmed rclone build: the same entry point as upstream rclone.go, but importing only the
// commands and backends BFE's Cloud and Remote screens use, so the downloadable binary stays small.
//
// The build-rclone.yml workflow copies this file into a checkout of rclone at the pinned tag as
// ./bfe/main.go and builds it with rclone's own go.mod, so every dependency version is exactly
// upstream's. To offer another service, add its backend import here and rebuild.
//
// rclone is MIT licensed (Nick Craig-Wood and contributors), see licenses/NOTICE_RCLONE.txt.
package main

import (
	"github.com/rclone/rclone/cmd"

	// Commands. rcd is the one BFE runs (the remote-control daemon behind every screen); the rest
	// keep the binary useful from a shell for diagnostics and match what rcd can do.
	_ "github.com/rclone/rclone/cmd/about"
	_ "github.com/rclone/rclone/cmd/authorize"
	_ "github.com/rclone/rclone/cmd/cat"
	_ "github.com/rclone/rclone/cmd/cleanup"
	_ "github.com/rclone/rclone/cmd/config"
	_ "github.com/rclone/rclone/cmd/copy"
	_ "github.com/rclone/rclone/cmd/copyto"
	_ "github.com/rclone/rclone/cmd/delete"
	_ "github.com/rclone/rclone/cmd/deletefile"
	_ "github.com/rclone/rclone/cmd/listremotes"
	_ "github.com/rclone/rclone/cmd/ls"
	_ "github.com/rclone/rclone/cmd/lsd"
	_ "github.com/rclone/rclone/cmd/lsf"
	_ "github.com/rclone/rclone/cmd/lsjson"
	_ "github.com/rclone/rclone/cmd/mkdir"
	_ "github.com/rclone/rclone/cmd/move"
	_ "github.com/rclone/rclone/cmd/moveto"
	_ "github.com/rclone/rclone/cmd/obscure"
	_ "github.com/rclone/rclone/cmd/purge"
	_ "github.com/rclone/rclone/cmd/rc"
	_ "github.com/rclone/rclone/cmd/rcd"
	_ "github.com/rclone/rclone/cmd/rmdir"
	_ "github.com/rclone/rclone/cmd/size"
	_ "github.com/rclone/rclone/cmd/sync"
	_ "github.com/rclone/rclone/cmd/touch"
	_ "github.com/rclone/rclone/cmd/version"

	// Cloud storage.
	_ "github.com/rclone/rclone/backend/b2"
	_ "github.com/rclone/rclone/backend/box"
	_ "github.com/rclone/rclone/backend/drive"
	_ "github.com/rclone/rclone/backend/dropbox"
	_ "github.com/rclone/rclone/backend/mega"
	_ "github.com/rclone/rclone/backend/onedrive"
	_ "github.com/rclone/rclone/backend/pcloud"
	_ "github.com/rclone/rclone/backend/protondrive"
	_ "github.com/rclone/rclone/backend/s3"
	_ "github.com/rclone/rclone/backend/yandex"

	// Servers (the Remote screen) - WebDAV doubles as Nextcloud / ownCloud on the Cloud screen.
	_ "github.com/rclone/rclone/backend/ftp"
	_ "github.com/rclone/rclone/backend/http"
	_ "github.com/rclone/rclone/backend/sftp"
	_ "github.com/rclone/rclone/backend/smb"
	_ "github.com/rclone/rclone/backend/webdav"

	// Local disk (the other end of every transfer) and the wrapping backends.
	_ "github.com/rclone/rclone/backend/combine"
	_ "github.com/rclone/rclone/backend/crypt"
	_ "github.com/rclone/rclone/backend/local"
	_ "github.com/rclone/rclone/backend/union"
)

func main() {
	cmd.Main()
}
