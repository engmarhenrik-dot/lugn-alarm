# Lugn alarm

Enkel alarm- och timer-app (tupplur) för Android med ett lugnt, mörkt ljud som stegras i detalj.

## Bygga och installera
1. Installera Android Studio (https://developer.android.com/studio).
2. `File > Open` och välj mappen `LugnAlarm`. Låt Gradle synka klart (laddar ner det som behövs).
3. Slå på **Utvecklaralternativ > USB-felsökning** på telefonen, anslut den och tryck på **Run** (▶).
   Eller `Build > Build APK(s)` och kopiera `app/build/outputs/apk/debug/app-debug.apk` till telefonen.

Kräver Android 10 eller nyare.

## Funktioner
- **Tupplur:** ange minuter (eller tryck 10/20/30/45/60) och starta. Räknar ner och ringer.
- **Alarm:** valfritt antal, med klockslag och valfria veckodagar (inga dagar = en gång).
- **Ljud och stegring** (knappen längst ner):
  - Grundton 55–165 Hz (lägre = mörkare) och hur mycket övertoner som hörs.
  - Separat stegring för alarm och tupplur: startvolym %, slutvolym %, tid i minuter
    (decimaler går bra, t.ex. 7,5), och kurva (linjär, mjuk start, S-kurva).
  - Provlyssna stegringen komprimerad till 8 sekunder.
- Ljudet syntetiseras i appen (djup dron av sinustoner som sakta svävar), inga ljudfiler.
- Procenten är procent av alarmvolymen. Som standard sätts alarmvolymen till max medan det ringer
  och återställs efteråt, så att 5 % faktiskt betyder 5 % av telefonens högsta nivå.
  Det går att stänga av i inställningarna.
- Ringer max en timme om du inte stänger av.

## Tips
- Tillåt notiser när appen frågar. På Android 14+ kan du behöva tillåta "fullskärmsnotiser"
  (appen visar en gul rad som leder dit) för att alarmet ska visas över låsskärmen.
- Telefonhögtalare återger djupa toner dåligt. Höj övertonerna om ljudet låter för tunt.
