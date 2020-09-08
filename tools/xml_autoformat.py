#!/usr/bin/env python3

# Copyright 2020 Hyun Woo Park
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.





import re
import collections
import sys

def trackLineCol(line, col, newText):
    for ch in newText:
        if ch == '\n':
            line += 1
            col = 1
        else:
            col += 1
    return line, col


def CreateTokenizer(tokenDefinitions):
    """General-purpose tokenizer generator"""
    tokenRuleList = [(name, re.compile(tokenDef, flags=re.I)) for name, tokenDef in tokenDefinitions]

    def Tokenize(s):
        tokens = []
        cur_pos = 0
        line = col = 1
        while cur_pos < len(s):
            matched = False

            for name, tokenRegex in tokenRuleList:
                match = tokenRegex.match(s, cur_pos)
                if match:
                    matchedstring = match.group(0)
                    if name:
                        tokens.append((name, matchedstring, (line, col)))
                    cur_pos += len(matchedstring)
                    line, col = trackLineCol(line, col, matchedstring)
                    matched = True
                    break

            if not matched:
                return (True, (line, col))

        return (None, tokens)

    return Tokenize


## ---------------------


# Tokenizer for entire XML document
xmlTokenizer = CreateTokenizer([
    (None, r'[ \t\r]+'),
    ('newline', '\n'),
    ('xmlheader', r"""<\?xml version=['"]1.0['"] encoding=['"]UTF-8['"]\?>"""),
    ('comment', r'<!--(.|\n)*?-->'),
    ('data', r'<!\[CDATA\[(.|\n)*?\]\]>'),
    ('xmltag', r'<(.|\n)*?>\n?'),
    ('data', r'[^<>]+'),
])

# Tokenizer for individual opening/closing tag
tagTokenizer = CreateTokenizer([
    (None, r'[ \t\r\n]+'),
    ('closing', '/'),
    ('name', r'[_\-a-zA-Z0-9]+'),
    ('eq', '='),
    ('string', r'\"([^\"]|\.)*\"'),
    ('string', r'\'([^\"]|\.)*\''),
])

def prettifyXML(xmlContent):
    lines = []
    indent = 0
    lastStr = ''

    def write(data):
        nonlocal lastStr
        if lastStr == '':
            lastStr = '    ' * indent

        lastStr += data
        if lastStr.endswith('\n'):
            lines.append(lastStr)
            lastStr = ''


    def writeTag(tagContent):
        nonlocal indent

        err, tagTokens = tagTokenizer(tagContent.strip()[1:-1])
        if err:
            return True

        # newline before writing tag
        if lastStr:
            write('\n')

        # closing tag
        if tagTokens[0][0] == 'closing':
            indent -= 1  # undo (**)
            write('</%s>\n' % tagTokens[1][1])
            return

        # opening tag w/o arguments
        if len(tagTokens) == 1:
            write('<%s>\n' % tagTokens[0][1])
            indent += 1
            return

        # opening tag with arguments
        write('<%s\n' % tagTokens[0][1])
        indent += 1  # (*)
        i = 1
        while i < len(tagTokens):
            # `arg="string"` type argument
            if (
                i <= len(tagTokens) - 3 and
                tagTokens[i + 0][0] == 'name' and
                tagTokens[i + 1][0] == 'eq' and
                tagTokens[i + 2][0] == 'string'
            ):
                write('%s=%s\n' % (
                    tagTokens[i + 0][1],
                    tagTokens[i + 2][1],
                ))
                i += 3

            # `arg` type argument w/o value
            elif tagTokens[i][0] == 'name':
                write('%s\n' % tagTokens[i][1])

            # self-closing tag
            elif tagTokens[i][0] == 'closing':
                assert i == len(tagTokens) - 1
                write('/>\n')
                indent -= 1
                return

            else:
                raise RuntimeError('Non-parsable tag syntax: %s' % str(tagTokens))

        write('>\n')
        indent -= 1  # undo (*)

        # For children (**)
        indent += 1

    err, xmlTokens = xmlTokenizer(xmlContent)
    if err:
        line, col = xmlTokens
        raise ValueError(f"""File {sys.argv[1]}, line {line}, col {col} parse failed""")

    for t, v, (line, col) in xmlTokens:
        if t == 'newline' or t == 'comment' or t == 'xmlheader':
            write(v)
        elif t == 'xmltag':
            err = writeTag(v)
            if err:
                raise ValueError(f"""File {sys.argv[1]}, line {line}, col {col}, tag "{v}" parse failed""")

        elif t == 'data':
            write(v.strip())

    return ''.join(lines)

if __name__ == '__main__':
    print(prettifyXML(
        open(sys.argv[1], 'r', encoding='utf-8').read()
    ), end="")
