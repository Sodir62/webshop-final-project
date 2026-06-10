// Project-report template — adapted from the FIIW KU Leuven master-thesis Typst template
// (Robin Schellemans, MIT). Trimmed to an English course-project report and made
// dependency-free (no @preview packages) so it compiles offline and self-contained.

#let accent = rgb("#1d4ed8")
#let ink = rgb("#1c1e21")

// ---- Diagram primitives (pure Typst, no packages) ---------------------------

// A service / component box: bold accent title over small body content.
#let dnode(title, body: none, fill: rgb("#eef2ff"), edge: accent, width: 100%) = rect(
  width: width, radius: 5pt, inset: 9pt, fill: fill, stroke: 0.9pt + edge,
)[
  #align(left)[
    #text(weight: "bold", size: 10pt, fill: edge)[#title]
    #if body != none [
      #v(3pt)
      #set text(size: 8.5pt, fill: ink)
      #body
    ]
  ]
]

// A downward flow arrow with an optional edge label.
#let adown(label: none) = align(center)[
  #text(size: 15pt, fill: luma(45%))[\u{25BC}]
  #if label != none [ #h(5pt) #text(size: 8pt, style: "italic", fill: luma(40%))[#label] ]
]

// ---- The document wrapper ---------------------------------------------------

#let report(
  title: none,
  subtitle: none,
  authors: (),       // array of strings
  team: none,        // team number / id
  course: none,
  institution: none,
  campus: none,
  coaches: (),       // array of strings
  period: none,      // e.g. "Academic year 2025–2026"
  abstract: none,    // optional content
  body,
) = {
  // author metadata omitted: authors may be styled content (placeholders), not plain strings.
  set document(title: title)
  set text(lang: "en", font: "New Computer Modern", size: 11pt, fill: ink)
  set par(justify: true, leading: 0.62em)
  set page(paper: "a4", margin: (x: 2.5cm, y: 2.5cm), numbering: none)

  // ---- Title page ----
  v(1.1cm)
  align(center, image("assets/logo.png", width: 4.2cm))
  v(1.7cm)
  align(center)[
    #text(size: 10.5pt, tracking: 2pt, fill: accent)[#upper(course)]
    #v(0.5cm)
    #line(length: 55%, stroke: 0.6pt + accent)
    #v(0.7cm)
    #text(size: 23pt, weight: "bold")[#title]
    #if subtitle != none [ #v(0.35cm) #text(size: 14pt, fill: luma(35%))[#subtitle] ]
    #v(1.4cm)
    #for a in authors [ #text(size: 12pt)[#a] \ ]
    #if team != none [ #v(0.2cm) #text(size: 10pt, fill: luma(40%))[Team #team] ]
  ]
  place(bottom + center)[
    #set text(size: 10pt, fill: luma(30%))
    #set par(leading: 0.7em)
    #if institution != none [ #institution \ ]
    #if campus != none [ #campus \ ]
    #if coaches.len() > 0 [ #v(0.3cm) Coaches: #coaches.join(", ") \ ]
    #if period != none [ #v(0.2cm) #period ]
  ]
  pagebreak()

  // ---- Front matter (abstract + contents), arabic page numbers, no header ----
  set page(numbering: "1")
  counter(page).update(1)

  if abstract != none {
    heading(outlined: false)[Abstract]
    abstract
    pagebreak()
  }

  show outline.entry.where(level: 1): set text(weight: "bold")
  outline(title: [Contents], indent: 1em)
  pagebreak()

  // ---- Main matter: numbered headings + running header ----
  set heading(numbering: "1.1")
  show heading: set block(above: 1.3em, below: 0.7em)
  show heading.where(level: 1): set text(size: 15pt)
  show heading.where(level: 2): set text(size: 12.5pt)
  show heading.where(level: 3): set text(size: 11pt)

  // Native running header: the current top-level section (hydra-style), with a rule.
  set page(header: context {
    let past = query(heading.where(level: 1).before(here()))
    if past.len() > 0 {
      set text(size: 9pt, style: "italic", fill: luma(45%))
      past.last().body
      v(-0.5em)
      line(length: 100%, stroke: 0.4pt + luma(70%))
    }
  })

  body
}
