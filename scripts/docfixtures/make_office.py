#!/usr/bin/env python3
"""
Builds the office fixtures for the document-viewer unit tests (app/src/test/resources/docs) that pandoc
can't write, and adds the parts pandoc doesn't emit to its .docx / .odt output (merged table cells,
coloured / sized text, a page break). Run by make_fixtures.sh; pure standard library.

    make_office.py <workdir> <outdir> [<libreoffice .odt to scrub>]

The optional third argument is a real LibreOffice document: its XML is kept (styles, automatic styles,
LibreOffice namespaces, soft page breaks) but every piece of text is replaced, and the thumbnail,
settings and metadata are dropped, so it can be committed without its content.
"""
import os
import re
import struct
import sys
import zipfile
import zlib


def png(rgb, w=8, h=8):
    raw = b''.join(b'\x00' + bytes(rgb) * w for _ in range(h))

    def chunk(t, d):
        return struct.pack('>I', len(d)) + t + d + struct.pack('>I', zlib.crc32(t + d) & 0xffffffff)
    return (b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 2, 0, 0, 0))
            + chunk(b'IDAT', zlib.compress(raw)) + chunk(b'IEND', b''))


def rewrite(src, dst, edits, extra=None):
    """Copies zip [src] to [dst], passing named entries through edit functions; mimetype stays first + stored."""
    with zipfile.ZipFile(src) as zin, zipfile.ZipFile(dst, 'w') as zout:
        names = zin.namelist()
        if 'mimetype' in names:
            zout.writestr(zipfile.ZipInfo('mimetype'), zin.read('mimetype'), compress_type=zipfile.ZIP_STORED)
        for n in names:
            if n == 'mimetype':
                continue
            data = zin.read(n)
            if n in edits:
                data = edits[n](data.decode('utf-8')).encode('utf-8')
            zout.writestr(n, data, compress_type=zipfile.ZIP_DEFLATED)
        for n, data in (extra or {}).items():
            zout.writestr(n, data, compress_type=zipfile.ZIP_DEFLATED)


def write_zip(dst, mimetype, files):
    with zipfile.ZipFile(dst, 'w') as z:
        if mimetype:
            z.writestr(zipfile.ZipInfo('mimetype'), mimetype, compress_type=zipfile.ZIP_STORED)
        for n, data in files.items():
            z.writestr(n, data, compress_type=zipfile.ZIP_DEFLATED)


# ── .docx: pandoc's output + a page break, coloured / sized runs and a merged-cell table ──
DOCX_EXTRA = '''
<w:p><w:r><w:br w:type="page"/></w:r></w:p>
<w:p><w:pPr><w:jc w:val="center"/></w:pPr><w:r><w:rPr><w:color w:val="C00000"/><w:sz w:val="48"/></w:rPr><w:t>Big red centred text</w:t></w:r></w:p>
<w:tbl><w:tblGrid><w:gridCol/><w:gridCol/><w:gridCol/></w:tblGrid>
<w:tr><w:tc><w:tcPr><w:gridSpan w:val="2"/></w:tcPr><w:p><w:r><w:t>Spans two columns</w:t></w:r></w:p></w:tc>
<w:tc><w:tcPr><w:vMerge w:val="restart"/></w:tcPr><w:p><w:r><w:t>Spans two rows</w:t></w:r></w:p></w:tc></w:tr>
<w:tr><w:tc><w:p><w:r><w:t>Cell A2</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>Cell B2</w:t></w:r></w:p></w:tc>
<w:tc><w:tcPr><w:vMerge/></w:tcPr><w:p/></w:tc></w:tr>
</w:tbl>
<w:p><w:r><w:fldChar w:fldCharType="begin"/></w:r><w:r><w:instrText xml:space="preserve"> PAGE </w:instrText></w:r><w:r><w:fldChar w:fldCharType="separate"/></w:r><w:r><w:t>FieldResult7</w:t></w:r><w:r><w:fldChar w:fldCharType="end"/></w:r></w:p>
'''


def docx_doc(xml):
    i = xml.rfind('<w:sectPr')
    return xml[:i] + DOCX_EXTRA + xml[i:]


# ── .odt: pandoc's output + a page break, coloured / sized text, a spanned table and a text:s run ──
ODT_STYLES = ('<style:style style:name="BfeBreak" style:family="paragraph"><style:paragraph-properties fo:break-before="page"/></style:style>'
              '<style:style style:name="BfeRed" style:family="text"><style:text-properties fo:color="#c00000" fo:font-size="24pt"/></style:style>')
ODT_EXTRA = '''
<text:p text:style-name="BfeBreak">After the page break</text:p>
<text:p>Spaced<text:s text:c="3"/>out and <text:span text:style-name="BfeRed">big red text</text:span><text:tab/>tabbed<text:line-break/>new line</text:p>
<table:table table:name="Merged">
  <table:table-column table:number-columns-repeated="3"/>
  <table:table-row>
    <table:table-cell table:number-columns-spanned="2"><text:p>Spans two columns</text:p></table:table-cell>
    <table:covered-table-cell/>
    <table:table-cell table:number-rows-spanned="2"><text:p>Spans two rows</text:p></table:table-cell>
  </table:table-row>
  <table:table-row>
    <table:table-cell><text:p>Cell A2</text:p></table:table-cell>
    <table:table-cell><text:p>Cell B2</text:p></table:table-cell>
    <table:covered-table-cell/>
  </table:table-row>
</table:table>
'''


def odt_content(xml):
    xml = xml.replace('</office:automatic-styles>', ODT_STYLES + '</office:automatic-styles>', 1)
    return xml.replace('</office:text>', ODT_EXTRA + '</office:text>', 1)


# ── .xlsx: two sheets, shared + inline strings, numbers, a date, a percentage, currency, a boolean, a
# formula's cached value and a merged range ──
XLSX = {
    '[Content_Types].xml': '''<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/sharedStrings.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/></Types>''',
    '_rels/.rels': '''<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>''',
    'xl/workbook.xml': '''<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Games" sheetId="1" r:id="rId1"/><sheet name="Notes" sheetId="2" r:id="rId2"/></sheets></workbook>''',
    'xl/_rels/workbook.xml.rels': '''<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="/xl/worksheets/sheet2.xml"/><Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings" Target="sharedStrings.xml"/><Relationship Id="rId4" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>''',
    'xl/sharedStrings.xml': '''<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="6" uniqueCount="6"><si><t>Game</t></si><si><t>Released</t></si><si><t>Score</t></si><si><t>Price</t></si><si><r><t>Half</t></r><r><rPr><b/></rPr><t>-Life</t></r></si><si><t>Merged title</t></si></sst>''',
    'xl/styles.xml': '''<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><numFmts count="2"><numFmt numFmtId="164" formatCode="yyyy-mm-dd"/><numFmt numFmtId="165" formatCode="&quot;$&quot;#,##0.00"/></numFmts><cellXfs count="5"><xf numFmtId="0"/><xf numFmtId="164" applyNumberFormat="1"/><xf numFmtId="10" applyNumberFormat="1"/><xf numFmtId="165" applyNumberFormat="1"/><xf numFmtId="3" applyNumberFormat="1"/></cellXfs></styleSheet>''',
    'xl/worksheets/sheet1.xml': '''<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>
<row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>1</v></c><c r="C1" t="s"><v>2</v></c><c r="D1" t="s"><v>3</v></c></row>
<row r="2"><c r="A2" t="s"><v>4</v></c><c r="B2" s="1"><v>36101</v></c><c r="C2" s="2"><v>0.96</v></c><c r="D2" s="3"><v>9.99</v></c></row>
<row r="3"><c r="A3" t="inlineStr"><is><t>Portal</t></is></c><c r="B3" s="1"><v>39373</v></c><c r="C3" s="2"><v>0.9</v></c><c r="D3" s="3"><v>1234.5</v></c></row>
<row r="4"><c r="A4" t="str"><f>A2&amp;"!"</f><v>Formula text</v></c><c r="B4" t="b"><v>1</v></c><c r="C4"><v>42</v></c><c r="D4" s="4"><v>1234567</v></c></row>
<row r="6"><c r="A6" t="s"><v>5</v></c></row>
</sheetData><mergeCells count="1"><mergeCell ref="A6:C7"/></mergeCells></worksheet>''',
    'xl/worksheets/sheet2.xml': '''<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData><row r="2"><c r="B2" t="inlineStr"><is><t>Second sheet cell</t></is></c></row></sheetData></worksheet>''',
}

# ── .ods: two sheets, repeated rows / columns (as LibreOffice writes empty space), a spanned cell, a date ──
ODF_NS = ('xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0" '
          'xmlns:style="urn:oasis:names:tc:opendocument:xmlns:style:1.0" '
          'xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0" '
          'xmlns:table="urn:oasis:names:tc:opendocument:xmlns:table:1.0" '
          'xmlns:draw="urn:oasis:names:tc:opendocument:xmlns:drawing:1.0" '
          'xmlns:fo="urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0" '
          'xmlns:xlink="http://www.w3.org/1999/xlink" '
          'xmlns:svg="urn:oasis:names:tc:opendocument:xmlns:svg-compatible:1.0" '
          'xmlns:presentation="urn:oasis:names:tc:opendocument:xmlns:presentation:1.0" '
          'xmlns:number="urn:oasis:names:tc:opendocument:xmlns:datastyle:1.0"')
ODS_CONTENT = f'''<?xml version="1.0" encoding="UTF-8"?>
<office:document-content {ODF_NS} office:version="1.3"><office:body><office:spreadsheet>
<table:table table:name="Scores">
<table:table-column table:number-columns-repeated="1024"/>
<table:table-row><table:table-cell office:value-type="string"><text:p>Player</text:p></table:table-cell><table:table-cell office:value-type="string"><text:p>Points</text:p></table:table-cell><table:table-cell office:value-type="string"><text:p>Date</text:p></table:table-cell><table:table-cell table:number-columns-repeated="1021"/></table:table-row>
<table:table-row><table:table-cell office:value-type="string"><text:p>Alice</text:p></table:table-cell><table:table-cell office:value-type="float" office:value="1500.5"><text:p>1,500.50</text:p></table:table-cell><table:table-cell office:value-type="date" office:date-value="2026-10-16"><text:p>16/10/26</text:p></table:table-cell><table:table-cell table:number-columns-repeated="1021"/></table:table-row>
<table:table-row><table:table-cell table:number-columns-spanned="2" office:value-type="string"><text:p>Spanned total</text:p></table:table-cell><table:covered-table-cell/><table:table-cell office:value-type="float" office:value="7"/><table:table-cell table:number-columns-repeated="1021"/></table:table-row>
<table:table-row table:number-rows-repeated="3"><table:table-cell office:value-type="string"><text:p>Repeated row</text:p></table:table-cell><table:table-cell table:number-columns-repeated="1023"/></table:table-row>
<table:table-row table:number-rows-repeated="1048570"><table:table-cell table:number-columns-repeated="1024"/></table:table-row>
</table:table>
<table:table table:name="Second"><table:table-row><table:table-cell office:value-type="string"><text:p>Other sheet</text:p></table:table-cell></table:table-row></table:table>
</office:spreadsheet></office:body></office:document-content>'''

# ── .odp: a title slide, a slide with a bulleted text box and an image, speaker notes ──
ODP_CONTENT = f'''<?xml version="1.0" encoding="UTF-8"?>
<office:document-content {ODF_NS} office:version="1.3"><office:body><office:presentation>
<draw:page draw:name="Intro">
  <draw:frame presentation:class="title" svg:x="1cm" svg:y="1cm"><draw:text-box><text:p>Welcome Slide Title</text:p></draw:text-box></draw:frame>
  <draw:frame presentation:class="subtitle" svg:x="1cm" svg:y="5cm"><draw:text-box><text:p>The subtitle line</text:p></draw:text-box></draw:frame>
</draw:page>
<draw:page draw:name="Details">
  <draw:frame svg:x="12cm" svg:y="4cm"><draw:image xlink:href="Pictures/blue.png"/></draw:frame>
  <draw:frame presentation:class="title" svg:x="1cm" svg:y="1cm"><draw:text-box><text:p>Second Slide Title</text:p></draw:text-box></draw:frame>
  <draw:frame presentation:class="outline" svg:x="1cm" svg:y="4cm"><draw:text-box><text:list><text:list-item><text:p>Point one</text:p></text:list-item><text:list-item><text:p>Point two</text:p></text:list-item></text:list></draw:text-box></draw:frame>
  <presentation:notes><draw:frame presentation:class="notes"><draw:text-box><text:p>Speaker note text</text:p></draw:text-box></draw:frame></presentation:notes>
</draw:page>
</office:presentation></office:body></office:document-content>'''

# ── .fodt: flat XML text document with an inline base64 picture ──
def fodt(img_b64):
    return f'''<?xml version="1.0" encoding="UTF-8"?>
<office:document {ODF_NS} office:version="1.3" office:mimetype="application/vnd.oasis.opendocument.text">
<office:styles><style:style style:name="Heading_20_1" style:display-name="Heading 1" style:family="paragraph" style:default-outline-level="1"/></office:styles>
<office:automatic-styles><style:style style:name="P1" style:family="paragraph" style:parent-style-name="Heading_20_1"/><style:style style:name="T1" style:family="text"><style:text-properties fo:font-weight="bold"/></style:style></office:automatic-styles>
<office:body><office:text>
<text:p text:style-name="P1">Flat Heading Via Style</text:p>
<text:p>Flat body with <text:span text:style-name="T1">bold flat</text:span> text.</text:p>
<text:p><draw:frame><draw:image><office:binary-data>{img_b64}</office:binary-data></draw:image></draw:frame></text:p>
</office:text></office:body></office:document>'''


def manifest(mime, extra=()):
    entries = ''.join(f'<manifest:file-entry manifest:full-path="{p}" manifest:media-type="{m}"/>' for p, m in extra)
    return ('<?xml version="1.0" encoding="UTF-8"?><manifest:manifest xmlns:manifest="urn:oasis:names:tc:opendocument:xmlns:manifest:1.0" manifest:version="1.3">'
            f'<manifest:file-entry manifest:full-path="/" manifest:media-type="{mime}"/>'
            '<manifest:file-entry manifest:full-path="content.xml" manifest:media-type="text/xml"/>' + entries + '</manifest:manifest>')


# ── A real LibreOffice .odt with its text replaced ──
WORDS = 'lorem ipsum dolor sit amet consectetur adipiscing elit sed do eiusmod tempor incididunt ut labore'.split()


def scrub_text(xml):
    counter = {'h': 0, 'p': 0}

    def replace_text(segment, kind):
        counter[kind] += 1
        n = counter[kind]
        if kind == 'h':
            return f'Section heading {n}'
        words = max(3, len(segment.split()))
        return f'Paragraph {n} ' + ' '.join(WORDS[(n + i) % len(WORDS)] for i in range(words - 2)) + '.'

    def sub_block(m):
        tag, attrs, inner = m.group(1), m.group(2) or '', m.group(3)
        kind = 'h' if tag == 'h' else 'p'
        # Keep the child elements (soft page breaks, spans), replace only the character data.
        parts = re.split(r'(<[^>]+>)', inner)
        text = ''.join(p for p in parts if not p.startswith('<'))
        if not text.strip():
            return m.group(0)
        out, placed = [], False
        for p in parts:
            if p.startswith('<'):
                out.append(p)
            elif p.strip() and not placed:
                out.append(replace_text(text, kind))
                placed = True
        return f'<text:{tag}{attrs}>' + ''.join(out) + f'</text:{tag}>'
    body_start = xml.find('<office:body>')
    head, body = xml[:body_start], xml[body_start:]
    # Self-closing (empty) paragraphs are skipped: the attribute group never ends in '/'.
    body = re.sub(r'<text:(h|p)((?:\s[^>]*?)?)(?<!/)>(.*?)</text:\1>', sub_block, body, flags=re.S)
    return head + body


def scrub_odt(src, dst):
    with zipfile.ZipFile(src) as zin, zipfile.ZipFile(dst, 'w') as zout:
        zout.writestr(zipfile.ZipInfo('mimetype'), zin.read('mimetype'), compress_type=zipfile.ZIP_STORED)
        for n in ('styles.xml', 'META-INF/manifest.xml'):
            data = zin.read(n).decode('utf-8')
            if n == 'META-INF/manifest.xml':
                data = re.sub(r'<manifest:file-entry manifest:full-path="(Thumbnails/thumbnail.png|settings.xml|meta.xml|manifest.rdf|Configurations2/)"[^>]*/>', '', data)
            zout.writestr(n, data, compress_type=zipfile.ZIP_DEFLATED)
        zout.writestr('content.xml', scrub_text(zin.read('content.xml').decode('utf-8')), compress_type=zipfile.ZIP_DEFLATED)


def main():
    work, out = sys.argv[1], sys.argv[2]
    import base64
    blue = png((30, 60, 220))
    rewrite(os.path.join(work, 'pandoc.docx'), os.path.join(out, 'sample.docx'), {'word/document.xml': docx_doc})
    rewrite(os.path.join(work, 'pandoc.odt'), os.path.join(out, 'sample.odt'), {'content.xml': odt_content})
    write_zip(os.path.join(out, 'sample.xlsx'), None, XLSX)
    write_zip(os.path.join(out, 'sample.ods'), 'application/vnd.oasis.opendocument.spreadsheet',
              {'META-INF/manifest.xml': manifest('application/vnd.oasis.opendocument.spreadsheet'), 'content.xml': ODS_CONTENT})
    write_zip(os.path.join(out, 'sample.odp'), 'application/vnd.oasis.opendocument.presentation',
              {'META-INF/manifest.xml': manifest('application/vnd.oasis.opendocument.presentation', [('Pictures/blue.png', 'image/png')]),
               'content.xml': ODP_CONTENT, 'Pictures/blue.png': blue})
    with open(os.path.join(out, 'sample.fodt'), 'w', encoding='utf-8') as f:
        f.write(fodt(base64.b64encode(blue).decode('ascii')))
    if len(sys.argv) > 3:
        scrub_odt(sys.argv[3], os.path.join(out, 'libreoffice-interview.odt'))


if __name__ == '__main__':
    main()
