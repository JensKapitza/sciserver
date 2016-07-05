# sciserver
Masterarbeit an der UDE, Tagging Peer-2-Peer System

Erste Version wird anfang November kommen.

Es gibt beim ersten commit einem menge Fehler, gerade weil verwendete Bibliotheken einige Fehler beinhalteten.
Es ist bereits jetzt die Idee die Kernarchitektur umzustellen, da die Kommunikation mit JMS nicht den erwünschten Erfolg brachte.

Idee: 
- nutzen von SSH und ggf. Portforwarding am Master zur Dateiübertragung.
- Nutzung von SSHFS


- master ersetzen mit payara-micro  mehr hier: https://payara.gitbooks.io/payara-server/content/documentation/payara-micro/payara-micro.html#3-starting-an-instance


Aktuelle Hints:
Dieses Projekt verwendet HornetQ, welches zu Apache Artemis wurde. 
Dadurch ist die hier verwendete Bibliothek stark veraltet!
Anpassungen am JMS sind nötig.




Finden der PID unter Linux durch das JNA Projekt:

import com.sun.jna.Library;
import com.sun.jna.Native;


interface CLibrary extends Library {
    CLibrary INSTANCE = (CLibrary) Native.loadLibrary("c", CLibrary.class);
    int getpid();
    int getppid();
    long time(long buf[]);

}


Im Programm dan nutzbar durch z.b.:

System.out.println(CLibrary.INSTANCE.getpid());



In Java 9 soll es einen einfachern Weg geben, um mit Prozessen zu Arbeiten, aber das dauert noch bis 2017 
