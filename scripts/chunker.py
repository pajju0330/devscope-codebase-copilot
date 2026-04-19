#!/usr/bin/env python3
"""
DevScope tree-sitter chunker.
Usage: python3 chunker.py <file_path> <language>
Outputs JSON array of code chunks to stdout.
"""

import sys
import json
import os

def chunk_file(file_path: str, language: str) -> list[dict]:
    try:
        import tree_sitter_java as tsjava
        import tree_sitter_python as tspython
        import tree_sitter_go as tsgo
        from tree_sitter import Language, Parser

        lang_map = {
            "java": (Language(tsjava.language()), _java_chunks),
            "python": (Language(tspython.language()), _python_chunks),
            "go": (Language(tsgo.language()), _go_chunks),
        }

        if language not in lang_map:
            return _fallback_chunks(file_path, language)

        lang_obj, extractor = lang_map[language]
        parser = Parser(lang_obj)

        with open(file_path, "rb") as f:
            source = f.read()

        tree = parser.parse(source)
        return extractor(source, tree, file_path)

    except ImportError:
        return _fallback_chunks(file_path, language)


def _java_chunks(source: bytes, tree, file_path: str) -> list[dict]:
    chunks = []
    source_str = source.decode("utf-8", errors="replace")

    class_name = None
    for node in tree.root_node.children:
        if node.type == "class_declaration":
            name_node = node.child_by_field_name("name")
            if name_node:
                class_name = source[name_node.start_byte:name_node.end_byte].decode("utf-8", errors="replace")
            _extract_java_methods(node, source, source_str, file_path, class_name, chunks)
        elif node.type in ("interface_declaration", "enum_declaration", "record_declaration"):
            name_node = node.child_by_field_name("name")
            if name_node:
                class_name = source[name_node.start_byte:name_node.end_byte].decode("utf-8", errors="replace")
            _extract_java_methods(node, source, source_str, file_path, class_name, chunks)

    if not chunks:
        # Fallback: whole file as one chunk
        chunks.append({
            "content": source_str[:4000],
            "class_name": None,
            "method_name": None,
            "start_line": 1,
            "end_line": source_str.count("\n") + 1,
            "language": "java"
        })
    return chunks


def _extract_java_methods(class_node, source: bytes, source_str: str,
                          file_path: str, class_name: str, chunks: list):
    for child in class_node.children:
        if child.type == "class_body":
            for member in child.children:
                if member.type in ("method_declaration", "constructor_declaration"):
                    name_node = member.child_by_field_name("name")
                    method_name = source[name_node.start_byte:name_node.end_byte].decode("utf-8", errors="replace") if name_node else None
                    content = source[member.start_byte:member.end_byte].decode("utf-8", errors="replace")
                    # Prepend class context
                    prefixed = f"// Class: {class_name}\n{content}" if class_name else content
                    chunks.append({
                        "content": prefixed[:4000],
                        "class_name": class_name,
                        "method_name": method_name,
                        "start_line": member.start_point[0] + 1,
                        "end_line": member.end_point[0] + 1,
                        "language": "java"
                    })


def _python_chunks(source: bytes, tree, file_path: str) -> list[dict]:
    chunks = []
    source_str = source.decode("utf-8", errors="replace")

    def walk(node, current_class=None):
        if node.type == "class_definition":
            name_node = node.child_by_field_name("name")
            class_name = source[name_node.start_byte:name_node.end_byte].decode("utf-8", errors="replace") if name_node else None
            for child in node.children:
                walk(child, class_name)
        elif node.type == "function_definition":
            name_node = node.child_by_field_name("name")
            method_name = source[name_node.start_byte:name_node.end_byte].decode("utf-8", errors="replace") if name_node else None
            content = source[node.start_byte:node.end_byte].decode("utf-8", errors="replace")
            prefix = f"# Class: {current_class}\n" if current_class else ""
            chunks.append({
                "content": (prefix + content)[:4000],
                "class_name": current_class,
                "method_name": method_name,
                "start_line": node.start_point[0] + 1,
                "end_line": node.end_point[0] + 1,
                "language": "python"
            })
        else:
            for child in node.children:
                walk(child, current_class)

    walk(tree.root_node)
    return chunks


def _go_chunks(source: bytes, tree, file_path: str) -> list[dict]:
    chunks = []
    source_str = source.decode("utf-8", errors="replace")
    for node in tree.root_node.children:
        if node.type == "function_declaration":
            name_node = node.child_by_field_name("name")
            method_name = source[name_node.start_byte:name_node.end_byte].decode("utf-8", errors="replace") if name_node else None
            content = source[node.start_byte:node.end_byte].decode("utf-8", errors="replace")
            chunks.append({
                "content": content[:4000],
                "class_name": None,
                "method_name": method_name,
                "start_line": node.start_point[0] + 1,
                "end_line": node.end_point[0] + 1,
                "language": "go"
            })
    return chunks


def _fallback_chunks(file_path: str, language: str) -> list[dict]:
    """Line-based fallback when tree-sitter is unavailable."""
    try:
        with open(file_path, "r", encoding="utf-8", errors="replace") as f:
            lines = f.readlines()
    except Exception:
        return []

    chunk_size = 60  # lines per chunk
    chunks = []
    for i in range(0, len(lines), chunk_size):
        block = lines[i:i + chunk_size]
        chunks.append({
            "content": "".join(block)[:4000],
            "class_name": None,
            "method_name": None,
            "start_line": i + 1,
            "end_line": min(i + chunk_size, len(lines)),
            "language": language
        })
    return chunks


if __name__ == "__main__":
    if len(sys.argv) < 3:
        print(json.dumps({"error": "Usage: chunker.py <file_path> <language>"}))
        sys.exit(1)

    file_path = sys.argv[1]
    language = sys.argv[2].lower()

    if not os.path.exists(file_path):
        print(json.dumps({"error": f"File not found: {file_path}"}))
        sys.exit(1)

    result = chunk_file(file_path, language)
    print(json.dumps(result))
