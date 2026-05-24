package com.adout.rule

class AhoCorasickMatcher {

    data class MatchResult(
        val pattern: String,
        val position: Int
    )

    private class Node {
        val children = mutableMapOf<Char, Node>()
        var failure: Node? = null
        val patterns = mutableListOf<String>()
        var isEnd = false
    }

    private val root = Node()
    private var isBuilt = false

    fun addPattern(pattern: String) {
        if (isBuilt) {
            throw IllegalStateException("Cannot add patterns after automaton is built")
        }

        val normalizedPattern = normalizePattern(pattern)
        var current = root

        for (char in normalizedPattern) {
            current = current.children.getOrPut(char) { Node() }
        }

        current.isEnd = true
        current.patterns.add(pattern)
    }

    fun build() {
        if (isBuilt) return

        val queue = ArrayDeque<Node>()

        // Set failure links for first-level nodes to root
        for (child in root.children.values) {
            child.failure = root
            queue.add(child)
        }

        // BFS to build failure links
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()

            for ((char, child) in current.children) {
                var failure = current.failure

                while (failure != null && !failure.children.containsKey(char)) {
                    failure = failure.failure
                }

                child.failure = failure?.children?.get(char) ?: root
                child.patterns.addAll(child.failure?.patterns ?: emptyList())

                queue.add(child)
            }
        }

        isBuilt = true
    }

    fun search(text: String): List<MatchResult> {
        if (!isBuilt) {
            build()
        }

        val results = mutableListOf<MatchResult>()
        var current = root
        val normalizedText = text.lowercase()

        for (i in normalizedText.indices) {
            val char = normalizedText[i]

            while (current != root && !current.children.containsKey(char)) {
                current = current.failure ?: root
            }

            current = current.children[char] ?: root

            for (pattern in current.patterns) {
                results.add(MatchResult(pattern, i))
            }
        }

        return results
    }

    fun match(domain: String): String? {
        val results = search(domain)
        return results.firstOrNull()?.pattern
    }

    private fun normalizePattern(pattern: String): String {
        var normalized = pattern.lowercase().trim()

        // Handle Adblock Plus format
        if (normalized.startsWith("||")) {
            normalized = normalized.substring(2)
        }
        if (normalized.endsWith("^")) {
            normalized = normalized.substring(0, normalized.length - 1)
        }

        // Handle wildcard prefix
        if (normalized.startsWith("*.")) {
            normalized = normalized.substring(2)
        }

        return normalized
    }

    fun clear() {
        root.children.clear()
        root.failure = null
        root.patterns.clear()
        isBuilt = false
    }

    fun getPatternCount(): Int {
        return root.children.size
    }
}
