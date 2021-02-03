<%--
    Copyright (C) 2016-2019 Code Defenders contributors
    This file is part of Code Defenders.
    Code Defenders is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or (at
    your option) any later version.
    Code Defenders is distributed in the hope that it will be useful, but
    WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
    General Public License for more details.
    You should have received a copy of the GNU General Public License
    along with Code Defenders. If not, see <http://www.gnu.org/licenses/>.
--%>

<%@ page import="java.util.Map" %>

<%--
    Displays the test code in a CodeMirror textarea.
--%>

<%-- <jsp:useBean id="testEditor" class="org.codedefenders.beans.game.TestEditorBean" scope="request"/> --%>
<jsp:useBean id="oldCode" class="org.codedefenders.beans.game.OldCode" scope="session"/>
<pre><textarea type="text" id="fileCreateCUT" name="fileCreateCUT" class="form-control" rows="20" required><%=  oldCode.getTestCode() %></textarea></pre>
<script>
(function () {
	let editorTest = CodeMirror.fromTextArea(document.getElementById("fileCreateCUT"), {
        lineNumbers: true,
        indentUnit: 4,
        smartIndent: true,
        matchBrackets: true,
        mode: "text/x-java",
        autoCloseBrackets: true,
        styleActiveLine: true,
        extraKeys: {
            "Ctrl-Space": "autocompleteTest",
            "Tab": "insertSoftTab"
        },
    });
    CodeMirror.commands.autocompleteTest = function (cm) {
        cm.showHint({
            hint: function (editor) {
                let reg = /[a-zA-Z][a-zA-Z0-9]*/;
                let list = autocompleteList;
                let cursor = editor.getCursor();
                let currentLine = editor.getLine(cursor.line);
                let start = cursor.ch;
                let end = start;
                while (end < currentLine.length && reg.test(currentLine.charAt(end))) ++end;
                while (start && reg.test(currentLine.charAt(start - 1))) --start;
                let curWord = start !== end && currentLine.slice(start, end);
                let regex = new RegExp('^' + curWord, 'i');
                return {
                    list: (!curWord ? list : list.filter(function (item) {
                        return item.match(regex);
                    })).sort(),
                    from: CodeMirror.Pos(cursor.line, start),
                    to: CodeMirror.Pos(cursor.line, end)
                };
            }
        });
    };
    editorTest.setSize("100%", 500);
})();
</script>