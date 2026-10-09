# 6-7 T2 Auction House (Fabric, Minecraft 1.21.11)

Taste **K** -> Auktionshaus. Muss auf dem **Server UND bei allen Spielern** im mods-Ordner liegen
(+ Fabric Loader >= 0.18.1 und Fabric API).

## Jar bauen
1. JDK 21 installieren.
2. Ordner in IntelliJ IDEA öffnen (Gradle-Import abwarten) -> Gradle-Task `build`
   ODER im Ordner:  `gradle wrapper --gradle-version 9.2.0`  und danach  `./gradlew build`  (Windows: `gradlew.bat build`)
3. Fertige Datei: `build/libs/hugo-auction-1.0.0.jar` (NICHT die -sources.jar)

## Ohne Installation bauen (GitHub)
1. Auf github.com ein neues Repository erstellen und den kompletten Ordnerinhalt hochladen (inkl. Ordner `.github`).
2. Reiter "Actions" -> "build" -> warten bis grüner Haken.
3. Auf den Lauf klicken -> unten bei "Artifacts" `hugo-auction-jar` herunterladen -> entpacken -> die .jar ist die Mod.
