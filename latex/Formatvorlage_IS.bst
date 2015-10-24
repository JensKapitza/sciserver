%NAME: Formatvorlage_IS.bst

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
%%% 
%%% Diese Stil-Datei wurde entwickelt von:
%%%
%%% Michael Müller
%%% 19.09.2007
%%% 
%%% Lehrstuhl Intelligente System
%%% Abteilung für Informatik und Angewandte Kognitionswissenschaft
%%% Fakultät für Ingenieurwissenschaften
%%% Universität Duisburg-Essen
%%% 
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
%%% 
%%% Dieser Stil unterstützt die folgenden Verweistypen mit den dazugehörigen Feldern. Optionale
%%% Felder sind in Klammern dargestellt:
%%% 
%%% article    : author, title, journal, volume, pages, year
%%% book       : author, title, (edition), (series), publisher, address, year
%%% conference : author, title, editor, booktitle, (series), pages, publisher, address, year
%%% internet   : organization, title, (edition), address, year, visited, URL
%%% manual     : organization, title, (edition), (series), address, year
%%% thesis     : author, title, type, school, address, year
%%% 
%%% Die erzeugten Abkürzungen haben die Form "[NachnameErsterAutor, Jahreszahl]" beziehungsweise
%%% "[ErstesWortOrganisation, Jahreszahl]". Mehrere gleiche Abkürzungen werden durch Anhängen von
%%% kleinen Buchstaben, beginnend bei "a", unterschieden. Ist eine Präambel definiert, wird diese
%%% ausgegeben.
%%% 
%%% Die Ausgaben erfolgen in deutscher Sprache. Für eine englischsprachige Arbeit kann die
%%% Ausgabe umgeschaltet werden, indem der Pseudo-Eintrag "en" referenziert wird, z.B. durch
%%% "\nocite{en}". Damit dieser Mechanismus funktioniert, müssen stets die beiden Pseudo-Einträge
%%% "de" und "en" vom Typ "language" vorhanden sein ("@language{de}" und "@language{en}").
%%%
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
%%%
%%% Die "FUNCTION {language}" setzt ein bestimmtes Verhalten des LaTeX-Übersetzers voraus.
%%% Ein abweichendes Verhalten könnte dazu führen, dass am Ende des Literaturverzeichnisses
%%% ein leeres Paar eckiger Klammern erscheint ("[]").
%%%
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%



%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
%%% Variablen
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%



ENTRY % verweisbezogene Variablen
{ % Feldnamen aus bib-Datei
  address
  author
  booktitle
  de
  edition
  editor
  en
  journal
  organization
  pages
  publisher
  school
  series
  title
  type
  URL
  visited
  volume
  year
  % Feld 'crossref' wird automatisch angelegt
}
{ % ganzzahlige Variablen
}
{ % Zeichenkettenvariablen
  % Variable 'sort.key$' wird automatisch angelegt und dient dem Kommando 'SORT' als Sortierindex; muss berechnet werden
  label % Abkürzung für den Eintrag (z.B. [Pauli, 2001]); muss berechnet werden
}

INTEGERS { % globale ganzzahlige Variablen
  % Variable 'entry.max$' wird automatisch angelegt
  % Variable 'global.max$' wird automatisch angelegt
}

STRINGS { % globale Zeichenkettenvariablen
  choosen.language % Festlegung der Sprache für einige Ausgaben
}



%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
%%% Hilfsfunktionen
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%



FUNCTION {not} {
    { #0 }
    { #1 }
  if$
}

FUNCTION {and} {
    'skip$
    { pop$ #0 }
  if$
}

FUNCTION {or} {
    { pop$ #1 }
    'skip$
  if$
}

FUNCTION {language.german} {
  choosen.language empty$
  choosen.language "de" =
  or
}

STRINGS {
  chop.word.s
  chop.word.substr
}
FUNCTION {chop.word} {
  'chop.word.substr :=
  'chop.word.s :=

  chop.word.substr chop.word.s #1 chop.word.substr text.length$ substring$ =
    {
      chop.word.s chop.word.substr text.length$ #1 + global.max$ substring$
    }
    'chop.word.s
  if$
}

INTEGERS {
  first.word.pos
}
STRINGS {
  first.word.s
}
FUNCTION {first.word} {
  " " * 'first.word.s :=
  #1 'first.word.pos :=

  {
    first.word.s first.word.pos #1 substring$ " " = not
  }
    {
      first.word.pos #1 + 'first.word.pos :=
    }
  while$

  first.word.s first.word.pos #1 - text.prefix$
}

INTEGERS {
  find.char.found
}
STRINGS {
  find.char.text
  find.char.char
}
FUNCTION {find.char} {
  'find.char.char :=
  'find.char.text :=
  #0 'find.char.found :=

  {
    find.char.found not
    find.char.text empty$ not
    and
  }
    {
      find.char.text #1 #1 substring$ find.char.char =
        {
          #1 'find.char.found :=
        }
        {
          find.char.text #2 global.max$ substring$ 'find.char.text :=
        }
      if$
    }
  while$

  find.char.found
}



%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
%%% Formatierungsfunktionen
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%



FUNCTION {begin.entry} {
  "  \bibitem[" label * "]{" * cite$ * "}" * write$ newline$
}

STRINGS {
  write.line.text
  write.line.separator
}
FUNCTION {write.line} {
  'write.line.separator :=
  'write.line.text :=
  "    " write.line.text * write.line.separator * write$ newline$
}

INTEGERS {
  format.names.pos
  format.names.left
}
STRINGS {
  format.names.names
}
FUNCTION {format.names} {
  'format.names.names :=
  #1 'format.names.pos :=
  format.names.names num.names$ 'format.names.left :=

  ""
    {
      format.names.left #0 >
    }
    {
      format.names.left #1 =
      format.names.pos #1 >
      and
        {
          format.names.pos #2 =
            {
              language.german
                {
                  " und "
                }
                {
                  " and "
                }
              if$
            }
            {
              language.german
                {
                  " und "
                }
                {
                  ", and "
                }
              if$
            }
          if$
        }
        {
          format.names.pos #1 >
            {
              ", "
            }
            {
              ""
            }
          if$
        }
      if$
      format.names.names format.names.pos "{ff~}{vv~}{ll}{, jj}" format.name$ * *

      format.names.pos #1 + 'format.names.pos :=
      format.names.left #1 - 'format.names.left :=
    }
  while$
}

FUNCTION {write.address} {
  address "," write.line
}

FUNCTION {write.author} {
  author format.names ":" write.line
}

FUNCTION {write.booktitle} {
  "{\em " booktitle * "}" * "." write.line
}

FUNCTION {write.edition} {
  edition missing$
    'skip$
    {
      edition "." write.line
    }
  if$
}

FUNCTION {write.editor} {
  language.german
    {
      " (Hrsg.)"
    }
    {
      editor num.names$ #1 >
        {
          " (editors)"
        }
        {
          " (editor)"
        }
      if$
    }
  if$

  "In " editor format.names * swap$ * ":" write.line
}

FUNCTION {write.journal} {
  "In {\em " journal * "}" * "," write.line
}

FUNCTION {write.organization} {
  organization ":" write.line
}

FUNCTION {write.pages} {
  pages "-" find.char
  pages "," find.char
  pages "+" find.char
  or or
    {
      language.german
        {
          "Seiten "
        }
        {
          "pages "
        }
      if$
    }
    {
      language.german
        {
          "Seite "
        }
        {
          "page "
        }
      if$
    }
  if$

  pages * "," write.line
}

FUNCTION {write.publisher} {
  publisher "," write.line
}

FUNCTION {write.school} {
  school "," write.line
}

FUNCTION {write.series} {
  series missing$
    'skip$
    {
      series "," write.line
    }
  if$
}

FUNCTION {write.title} {
  "{\em " title * "}" * "." write.line
}

FUNCTION {write.type} {
  type "." write.line
}

FUNCTION {write.URL} {
  "\vspace{0.2cm} \\ \url{" url * "}" * "" write.line
}

FUNCTION {write.visited} {
  language.german
    {
      "Stand: "
    }
    {
      "Date: "
    }
  if$

  visited * "." write.line
}

FUNCTION {write.volume} {
  volume "," write.line
}

FUNCTION {write.year} {
  year "." write.line
}



%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
%%% Funktionen für Verweistypen
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%



FUNCTION {default.type} {
  "Unbekannter Typ!" warning$
}

FUNCTION {article} {
  begin.entry
  write.author
  write.title
  write.journal
  write.volume
  write.pages
  write.year
}

FUNCTION {book} {
  begin.entry
  write.author
  write.title
  write.edition
  write.series
  write.publisher
  write.year
}

FUNCTION {conference} {
  begin.entry
  write.author
  write.title
  write.editor
  write.booktitle
  write.series
  write.pages
  write.publisher
  write.address
  write.year
}

FUNCTION {internet} {
  begin.entry
  write.organization
  write.title
  write.year
  write.visited
  write.URL
}

FUNCTION {language} {
  % zumindest ein leerer Text muss für diesen Pseudo-Eintrag geschrieben werden
  % erwartetes Verhalten: wird der erscheinende Text "[]" durch "{}" geklammert, verschwindet er
  "  {\bibitem[]{" cite$ * "}}" * write$ newline$
}

FUNCTION {manual} {
  begin.entry
  write.author
  write.title
  write.booktitle
  write.year
}

FUNCTION {thesis} {
  begin.entry
  write.author
  write.title
  write.type
  write.school
  write.address
  write.year
}



%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
%%% vom Programm verwendete Funktionen
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%



FUNCTION {create.label} {
  type$ "article" =
  type$ "book" =
  type$ "conference" =
  type$ "manual" =
  or or or
    {
      author missing$
        {
          "Autor fehlt!" warning$
        }
        {
          author #1 "{ll}" format.name$ 'label :=
        }
      if$
    }
    {
      type$ "internet" =
      type$ "thesis" =
      or
        {
          organization missing$
            {
              "Organisation fehlt!" warning$
            }
            {
              organization "The " chop.word first.word 'label :=
            }
          if$
        }
        {
          type$ "language" =
            'skip$
            {
              "Unbekannter Typ!" warning$
            }
          if$
        }
      if$
    }
  if$

  year missing$
    {
      type$ "language" =
        'skip$
        {
          "Jahr fehlt!" warning$
        }
      if$
    }
    {
      label ", " * year * 'label :=
    }
  if$
}

FUNCTION {create.sort.key} {
  type$ "language" =
    {
      "zzz" 'sort.key$ :=
    }
    {
      label title * 'sort.key$ :=
    }
  if$
}

STRINGS {
  change.duplicate.labels.last.label
  change.duplicate.labels.sublabel
}
FUNCTION {change.duplicate.labels.init} {
  "" 'change.duplicate.labels.last.label :=
}
% aufeinanderfolgende gleiche Abkürzungen durch Anhängen von Buchstaben
% beginnend bei "b" unterscheidbar machen
FUNCTION {change.duplicate.labels.iterate} {
  type$ "language" =
    'skip$
    {
      change.duplicate.labels.last.label label =
        {
          label 'change.duplicate.labels.last.label :=
          label change.duplicate.labels.sublabel * 'label :=
          change.duplicate.labels.sublabel chr.to.int$ #1 + int.to.chr$ 'change.duplicate.labels.sublabel :=
        }
        {
          label 'change.duplicate.labels.last.label :=
          "b" 'change.duplicate.labels.sublabel :=
        }
      if$
    }
  if$
}
FUNCTION {change.duplicate.labels.reverse} {
% "a" an erste Abkürzung einer Folge von gleichen Abkürzungen anhängen
  type$ "language" =
    'skip$
    {
      change.duplicate.labels.last.label change.duplicate.labels.last.label text.length$ #1 substring$ "b" =
        {
          label "a" * 'label :=
        }
        'skip$
      if$
      label 'change.duplicate.labels.last.label :=
    }
  if$
}

STRINGS {
  get.language.s
}
FUNCTION {get.language} {
  type$ "language" =
    {
      cite$ "l" change.case$ 'get.language.s :=
      get.language.s "de" = not
      get.language.s "en" = not
      and
        {
          "Unbekannte Sprache, nur 'de' und 'en' erlaubt. Verwende 'de'." warning$
          "de" 'choosen.language :=
        }
        {
          get.language.s 'choosen.language :=
        }
      if$
    }
    'skip$
  if$
}

FUNCTION {begin.bibliography} {
  preamble$ empty$
    'skip$
    {
      preamble$ write$ newline$
    }
  if$
  "\begin{thebibliography}{\hspace{1cm}}" write$ newline$
}

FUNCTION {end.bibliography} {
  "\end{thebibliography}" write$ newline$
}



%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
%%% Programm
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%



% Einlesen des Literaturverzeichnisses (bib-Datei)
READ

% Abkürzungen und Sortierschlüssel erzeugen
ITERATE {create.label}
ITERATE {create.sort.key}

% Sortierung durchführen
SORT

% potentiell doppelte Abkürzungen durch Anhängen von Buchstaben eindeutig machen
ITERATE {change.duplicate.labels.init}
ITERATE {change.duplicate.labels.iterate}
ITERATE {change.duplicate.labels.init}
REVERSE {change.duplicate.labels.reverse}

% Sprache ermitteln und Literaturverzeichnis schreiben
ITERATE {get.language}
EXECUTE {begin.bibliography}
ITERATE {call.type$}
EXECUTE {end.bibliography}

%
