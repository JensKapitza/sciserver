# sciserver
Masterarbeit an der UDE, Tagging Peer-2-Peer System

Erste Version wird anfang November kommen.

Es gibt beim ersten commit einem menge Fehler, gerade weil verwendete Bibliotheken einige Fehler beinhalteten.
Es ist bereits jetzt die Idee die Kernarchitektur umzustellen, da die Kommunikation mit JMS nicht den erwünschten Erfolg brachte.

Idee: 
- nutzen von SSH und ggf. Portforwarding am Master zur Dateiübertragung.
- Nutzung von SSHFS


Aktuelle Hints:
Dieses Projekt verwendet HornetQ, welches zu Apache Artemis wurde. 
Dadurch ist die hier verwendete Bibliothek stark veraltet!
Anpassungen am JMS sind nötig.
