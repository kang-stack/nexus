<template>
  <div class="md-content" v-html="rendered" />
</template>

<script setup>
import { computed } from 'vue'
import { marked } from 'marked'

marked.setOptions({
  breaks: true,
  gfm: true
})

const props = defineProps({ content: String })

const rendered = computed(() => {
  if (!props.content) return ''
  return marked.parse(props.content)
})
</script>

<style>
/* ====== Markdown Content Styles ====== */
.md-content {
  font-size: 14px;
  line-height: 1.75;
  color: inherit;
}

.md-content > *:first-child { margin-top: 0; }
.md-content > *:last-child { margin-bottom: 0; }

/* Headings */
.md-content h1, .md-content h2, .md-content h3,
.md-content h4, .md-content h5, .md-content h6 {
  margin: 1em 0 0.5em;
  font-weight: 600;
  line-height: 1.4;
  color: var(--text);
}
.md-content h1 { font-size: 1.3em; }
.md-content h2 { font-size: 1.15em; }
.md-content h3 { font-size: 1.05em; }

/* Paragraphs */
.md-content p { margin: 0.6em 0; }

/* Inline code */
.md-content code {
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 0.88em;
  font-family: 'JetBrains Mono', 'Fira Code', 'Cascadia Code', 'Consolas', monospace;
  background: rgba(56, 189, 248, 0.1);
  color: #0ea5e9;
}

/* Code blocks */
.md-content pre {
  margin: 0.8em 0;
  padding: 14px 16px;
  border-radius: var(--radius-sm);
  background: #1a1826;
  overflow-x: auto;
  line-height: 1.55;
}
.md-content pre code {
  padding: 0;
  background: none;
  color: #e2e0f0;
  font-size: 0.84em;
  border-radius: 0;
}

/* Blockquotes */
.md-content blockquote {
  margin: 0.6em 0;
  padding: 6px 14px;
  border-left: 3px solid #38bdf8;
  background: rgba(56, 189, 248, 0.04);
  border-radius: 0 2px 2px 0;
  color: #4a5c6c;
}

/* Lists */
.md-content ul, .md-content ol {
  margin: 0.5em 0;
  padding-left: 1.6em;
}
.md-content li { margin: 0.25em 0; }
.md-content li > ul, .md-content li > ol { margin: 0.1em 0; }

/* Task list */
.md-content input[type="checkbox"] {
  margin-right: 6px;
  accent-color: #38bdf8;
}

/* Tables */
.md-content table {
  width: 100%;
  margin: 0.8em 0;
  border-collapse: collapse;
  font-size: 0.92em;
}
.md-content th {
  padding: 8px 12px;
  background: rgba(56, 189, 248, 0.06);
  border-bottom: 2px solid var(--border-strong);
  font-weight: 600;
  text-align: left;
  color: var(--text);
}
.md-content td {
  padding: 7px 12px;
  border-bottom: 1px solid #e4e8ec;
  color: #4a5c6c;
}
.md-content tr:last-child td { border-bottom: none; }
.md-content tr:hover td { background: rgba(56, 189, 248, 0.03); }

/* Links */
.md-content a {
  color: var(--primary);
  text-decoration: underline;
  text-underline-offset: 2px;
}
.md-content a:hover { color: var(--primary-hover); }

/* Horizontal rules */
.md-content hr {
  margin: 1em 0;
  border: none;
  height: 1px;
  background: var(--border);
}

/* Images */
.md-content img {
  max-width: 100%;
  border-radius: var(--radius-xs);
  margin: 0.4em 0;
}

/* Strong / emphasis */
.md-content strong { font-weight: 600; color: var(--text); }
.md-content em { font-style: italic; }

/* Description lists */
.md-content dl { margin: 0.5em 0; }
.md-content dt { font-weight: 600; margin-top: 0.5em; }
.md-content dd { margin-left: 1.2em; color: var(--text-secondary); }
</style>
