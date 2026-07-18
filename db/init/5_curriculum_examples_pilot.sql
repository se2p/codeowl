-- ════════════════════════════════════════════════════════════════════
-- 5_curriculum_examples_pilot.sql
-- AP32 — Curated few-shot code examples ("Fewshot-Curriculum-Bibliothek")
-- ════════════════════════════════════════════════════════════════════
--
-- Source: curated BlueJ reference examples (4 BlueJ Java projects).
-- One row per project; `code` = all classes of the project concatenated.
--
-- Topic mapping:
--   Arrays und Listen (Jgst 10, b1000000-…001): Hund/Person/Tierarzt,
--       Fluggesellschaft, Vereinsverwaltung
--   Klassen und Objekte (Jgst 9, b9…005):        Kredit/Kunde
--
-- HOW TO RUN (existing / server DB — db/init only runs on a fresh volume):
--   docker exec -i codeowl-clean-db psql -U postgres -d codeowl < db/init/5_curriculum_examples_pilot.sql
--   psql "postgresql://USER:PASS@HOST:5432/codeowl" -f db/init/5_curriculum_examples_pilot.sql
--
-- IDEMPOTENT: fixed ids + ON CONFLICT (id) DO UPDATE — safe to re-run.
-- ════════════════════════════════════════════════════════════════════

SET client_encoding TO 'UTF8';

BEGIN;

INSERT INTO curriculum_code_examples
    (id, topic_id, language, title, code, problem_statement, difficulty, notes)
VALUES
    (
        'c9000000-0000-0000-0000-000000000001',
        'b1000000-0000-0000-0000-000000000001',
        'java',
        'Referenzen und Arrays – Mehr als ein Hund',
        $java$// ===== HUND.java =====
public class HUND{
    String name;
    boolean hatChip;
    int id;
    String nachnameDesHalters;
    
    public HUND(String nameEingabe){
        name = nameEingabe;
    }
    
    public void gibLaut(){
        System.out.println("Wuff!");
    }
    
    public void suchDenBall(){
        System.out.println("Such, such, such...! Huhuuu!");
    }
    
    public String nameZurueckgeben(){
        return name;
    }
 
    
    public void chippen(int idEingabe, String nameEingabe){
        id = idEingabe;
        nachnameDesHalters = nameEingabe;
        hatChip = true;
    }
    
    public String nachnameDesHaltersZurueckgeben(){
        return nachnameDesHalters;
    }
       
    public boolean hatChipZurueckgeben(){
        return hatChip;
    }
    
    public int idZurueckgeben(){
        return id;
    }
}

// ===== PERSON.java =====
public class PERSON{
    String nachname;
    String vorname;

    /**Ändere das Referenzattribut vom Datentyp HUND in den
     * Datentyp HUND [ ] („Hundearray“) und passe die Bezeichnung an (meineHunde).
     */
    HUND[] meineHunde;

    /**
     * Ergänze ein Attribut anzahlGekaufteHunde;
     */
    int anzahlGekaufteHunde;

    public PERSON(String nachnameEingabe, String vornameEingabe){
        nachname = nachnameEingabe;
        vorname = vornameEingabe;
        //Array auf die Welt bringen!
        meineHunde = new HUND[20];
        anzahlGekaufteHunde = 0;
    }

    /**Ändere die Methode hundKaufen(HUND h) so, dass jeder neu gekaufte Hund
     * im nächsten freien Element des „Hundearrays“ referenziert wird.
     */
    public void hundKaufen(HUND h){
        meineHunde[anzahlGekaufteHunde] = h;
        anzahlGekaufteHunde = anzahlGekaufteHunde + 1;
    }

    /**Ändere nun auch die Methode mitHundenSpielen() so,
     * dass nacheinander mit allen Hunden einmal gespielt wird.
     */
    public void mitHundenSpielen(){
        for(int i=0; i<anzahlGekaufteHunde; i= i+1){
            System.out.println("Auf geht's " + meineHunde[i].nameZurueckgeben());
            meineHunde[i].gibLaut();
            meineHunde[i].suchDenBall();
        }
    }

    public String nachnameZurueckgeben(){
        return nachname;
    }

    public String vornameZurueckgeben(){
        return vorname;
    }

}

// ===== TIERARZT.java =====
public class TIERARZT{
    String praxisname;
    
    HUND patientAktuell;
    
    
    public TIERARZT(String praxisnameEingabe){
        praxisname = praxisnameEingabe;
    }
    
    public void patientAktuellFestlegen(HUND h){
        patientAktuell = h;
    }
    
    public void patientAktuellChippen(int id, String nachnameDesBesitzers){
        if(patientAktuell.hatChipZurueckgeben()==false){
            patientAktuell.chippen(id, nachnameDesBesitzers);
        }else{
            System.out.println("Dieses Tier ist bereits gechippt!");
        }
    }
}
$java$,
        'Eine PERSON kann mehrere Hunde besitzen. Ändere das Referenzattribut vom Typ HUND in ein Array (HUND[] meineHunde) und ergänze ein Attribut anzahlGekaufteHunde. Passe hundKaufen(HUND h) so an, dass jeder neue Hund im nächsten freien Feldelement referenziert wird, und mitHundenSpielen() so, dass nacheinander mit allen Hunden gespielt wird. Ein TIERARZT verwaltet einen aktuellen Patienten (Referenz auf HUND) und chippt ihn nur, falls er noch keinen Chip hat.',
        'medium',
        'Einstiegsbeispiel für den Übergang von einem einzelnen Referenzattribut zu einem Array von Objekten. Betont: Objektreferenzen im Feld speichern, Zählvariable für die tatsächliche Anzahl, index-basierte for-Schleife, Methodenaufrufe auf Array-Elementen. Typische Fehler: Zähler nicht erhöhen, über das Feldende hinaus iterieren.'
    ),
    (
        'c9000000-0000-0000-0000-000000000002',
        'b9000000-0000-0000-0000-000000000005',
        'java',
        'Kreditverwaltung – Referenz auf Kunde',
        $java$// ===== KUNDE.java =====
public class KUNDE{
    
    //Attribute
    String kundeName;
    int kundeAlter;
    int kundeGehalt;

    public KUNDE(String kN, int kA, int kG){
        kundeName = kN;
        kundeAlter = kA;
        kundeGehalt = kG;
    }
    
    public void kundeNameAendern(String kN){
        kundeName = kN;
    }
    
    public String kundeNameZurueckgeben(){
        return kundeName;
    }
    
    public void kundeAlterAendern(int kA){
        kundeAlter = kA;
    }
    
    public int kundeAlterZurueckgeben(){
        return kundeAlter;
    }
    
    public void kundeGehaltAendern(int kG){
        kundeGehalt = kG;
    }
    
    public int kundeGehaltZurueckgeben(){
        return kundeGehalt;
    }
    
}

// ===== KREDIT.java =====
public class KREDIT{
    //Referenzattribut
    KUNDE kreditnehmer;    
    //Attribute
    int kreditID;
    double restschuld;
    int zinsProJahrInProzent;
    int tilgungProJahrInProzent;
    int anzahlMonatsmietenImRueckstand;
    boolean kreditIstRisikoFuerBank;
    
    //Konstruktor
    public KREDIT(KUNDE kundeEingabe, int kID, double rs, int zins, int tilgung){
        kreditnehmer = kundeEingabe; 
        kreditID = kID;
        restschuld = rs;
        zinsProJahrInProzent = zins;
        tilgungProJahrInProzent = tilgung;
        anzahlMonatsmietenImRueckstand = 0;
        kreditIstRisikoFuerBank = false;
    }
    
    public void kreditIstRisikoFuerBankNeuBewerten(){
        if(kreditnehmer.kundeGehaltZurueckgeben()<2000 && anzahlMonatsmietenImRueckstand>2){
            kreditIstRisikoFuerBank = true;
            System.out.println("Der Kredit mit der ID " + kreditID + " des Kunden " + kreditnehmer.kundeNameZurueckgeben() + " ist ein Risiko für die Bank!");
            System.out.println("Grund: Das Gehalt des Kunden liegt aktuell bei nur " + kreditnehmer.kundeGehaltZurueckgeben() + " Euro und es sind " + anzahlMonatsmietenImRueckstand + " ausstehend.");
        }else{ 
            if(anzahlMonatsmietenImRueckstand>4){
                kreditIstRisikoFuerBank = true;
                System.out.println("Der Kredit mit der ID " + kreditID + " des Kunden " + kreditnehmer.kundeNameZurueckgeben() + " IST EIN RISIKO für die Bank!");
                System.out.println("Grund: Es sind " + anzahlMonatsmietenImRueckstand + " ausstehend.");
            }else{
                kreditIstRisikoFuerBank = false;
                System.out.println("Der Kredit mit der ID " + kreditID + " des Kunden " + kreditnehmer.kundeNameZurueckgeben() + " IST KEIN RISIKO für die Bank!");
            }
        }
    }
    
    public void monatlicheRateEinzahlen(){
        restschuld = restschuld - (zinsProJahrInProzent/100+tilgungProJahrInProzent/100)/12*restschuld;
    }
    
    public void anzahlMonatsmietenImRueckstandErhoehen(){
        anzahlMonatsmietenImRueckstand = anzahlMonatsmietenImRueckstand + 1;
    }
    
    public void anzahlMonatsmietenImRueckstandVermindern(){
        anzahlMonatsmietenImRueckstand = anzahlMonatsmietenImRueckstand - 1;
    }
    
    public void kreditIDAendern(int kID){
        kreditID = kID;
    }
    
    public int kreditIDZurueckgeben(){
        return kreditID;
    }
    
    public double monatlicheRateZurueckgeben(){
        return (zinsProJahrInProzent/100)/12*restschuld;
    }
    
    public boolean kreditIstRisikoFuerBankZurueckgeben(){
        return kreditIstRisikoFuerBank;
    }
}
$java$,
        'Ein KREDIT verweist über ein Referenzattribut auf den KUNDEN und speichert Restschuld, Zins- und Tilgungssatz sowie ausstehende Monatsraten. Implementiere kreditIstRisikoFuerBankNeuBewerten(), die mit geschachtelten Bedingungen (Gehalt des Kunden, Rückstand) entscheidet, ob der Kredit ein Risiko für die Bank ist, sowie Methoden zum Einzahlen der monatlichen Rate und zum Verändern des Rückstands.',
        'medium',
        'Fokus auf Referenz/Assoziation zwischen zwei Klassen (KREDIT kennt KUNDE) und geschachtelte Selektion für eine Risikobewertung. Betont: Zugriff auf Attribute des referenzierten Objekts über Getter, mehrstufige if/else-Logik, boolesche Zustandsattribute. Bewusst ohne Array als reines Referenz- und Bedingungsbeispiel.'
    ),
    (
        'c9000000-0000-0000-0000-000000000003',
        'b1000000-0000-0000-0000-000000000001',
        'java',
        'Fluggesellschaft – Flotte als Array',
        $java$// ===== FLUGZEUG.java =====
public class FLUGZEUG{
   int flugzeugNummer;
   String typ;
   double masse;
   int plaetze;
   boolean istVonDerFlugsicherungAbgenommen;
   
   public FLUGZEUG(int flugzeugNummerEingabe, String typEingabe, double masseEingabe, int plaetzeEingabe) {
        flugzeugNummer = flugzeugNummerEingabe;
        typ = typEingabe;
        masse = masseEingabe;
        plaetze = plaetzeEingabe;
        istVonDerFlugsicherungAbgenommen = false;
    }
    
    public void istVonDerFlugsicherungAbgenommenSetzen(int passwortEingabe){
        if(passwortEingabe == 7834){
            System.out.println("Korrektes Passwort wurde eingegeben.");
            System.out.println("Das Flugzeug mit der Nummer + " + flugzeugNummer + " ist nun von der Flugsicherung abgenommen und darf eingesetzt werden.");
            istVonDerFlugsicherungAbgenommen = true;
        }else{
            System.out.println("Es wurde ein falsches Passwort eingegeben! Eintragung nicht erfolgreich.");
        }
    }
    
    public boolean istVonDerFlugsicherungAbgenommenZurueckgeben(){
        return istVonDerFlugsicherungAbgenommen;
    }
    
    public int flugzeugNummerZurueckgeben(){
        return flugzeugNummer;
    }
    
    public void flugzeugNummerSetzen(int nummerNeu){
            flugzeugNummer = nummerNeu;
    }
    
    public String typZurueckgeben(){
        return typ;
    }
    
    public void masseSetzen(double masseNeu){
        masse = masseNeu;
    }
    
    public double masseZurueckgeben(){
        return masse;
    }
    
    public void typSetzen(String typNeu){
        typ = typNeu;
    }
    
    public int plaetzeZurueckgeben(){
        return plaetze;
    }
    
    public void plaetzeSetzen(int plaetzeNeu){
        plaetze = plaetzeNeu;
    }
   
}

// ===== FLUGGESELLSCHAFT.java =====
public class FLUGGESELLSCHAFT {
    //Attribute
    String name;
    String steuerID;
    int aktuelleFlugzeugAnzahl;
    //Deklariere ein Feld, das Flugzeugobjekte speichern kann
    FLUGZEUG [] flotte;
    
    public FLUGGESELLSCHAFT (String nameEingabe, String steuerIDEingabe) {
        name = nameEingabe;
        steuerID = steuerIDEingabe;
        aktuelleFlugzeugAnzahl = 0;
        //erzeuge hier das Feld für maximal 20 Flugzeuge
        flotte = new FLUGZEUG[20];
    }
    
    //Schreibe eine Methode, die ein Flugzeugobjekt im Feld speichert
    public void flugzeugErfassen(FLUGZEUG flugzeugObjekt){
        flotte[aktuelleFlugzeugAnzahl] = flugzeugObjekt;
        aktuelleFlugzeugAnzahl = aktuelleFlugzeugAnzahl + 1;
    }
    
    //Mit Hilfe dieser Methode soll bei allen Flugzeugobjekten die Methode abnehmen(int passwortEingabe)
    //aufgerufen werden (vgl. Klasse FLUGZEUG; hier ist auch das korrekte Passwort zu finden).
    public void abnahmeDurchFlugsicherungBeiAllenFlugzeugenEintragen(int passwortEingabe){
        for(int i=0; i<aktuelleFlugzeugAnzahl; i = i+1){
            flotte[i].istVonDerFlugsicherungAbgenommenSetzen(passwortEingabe);
        }
    }
    
    //Mit Hilfe dieser Methode soll die Summe der Plätze von allen Flugzeugobjekten berechnet werden.
    //Das Ergebnis soll am Ende mit System.out.println(...) ausgegeben werden.
    public void gesamtzahlPlaetzeAllerFlugzeugeDerFlotteAusgeben(){
        int summePlaetze = 0;
        for(int i=0; i<aktuelleFlugzeugAnzahl; i=i+1){
            summePlaetze = summePlaetze + flotte[i].plaetzeZurueckgeben();
        }
        System.out.println("Die Gesamtzahl der Plätze aller Flugzeuge der Flotte beträgt: " + summePlaetze);
    }
    
    //Mit Hilfe dieser Methode soll die Anzahl der Flugzeuge bestimmt werden, die eine Masse von unter 5,7 Tonnen haben
    //Das Ergebnis soll am Ende mit System.out.println(...) ausgegeben werden.
    public void anzahlLeichtflugzeugeBestimmen(){
        int anzahlLeichtflugzeuge = 0;
        for(int i=0; i<aktuelleFlugzeugAnzahl; i=i+1){
            if(flotte[i].masseZurueckgeben() < 5.7){
                anzahlLeichtflugzeuge = anzahlLeichtflugzeuge + 1;
            }
        }
        System.out.println("In der Flotte sind/ist aktuell " + anzahlLeichtflugzeuge + " Leichtflugzeug(e).");
    }
}
$java$,
        'Eine FLUGGESELLSCHAFT verwaltet ihre Flotte in einem Array von FLUGZEUG-Objekten (maximal 20). Schreibe Methoden, um ein Flugzeug zu erfassen, bei allen Flugzeugen die Abnahme durch die Flugsicherung einzutragen (nur mit korrektem Passwort), die Gesamtzahl aller Plätze zu berechnen und die Anzahl der Leichtflugzeuge (Masse unter 5,7 Tonnen) zu bestimmen.',
        'medium',
        'Klassisches Array-von-Objekten-Beispiel mit drei Iterationsmustern: (1) auf jedem Element eine Methode aufrufen, (2) einen Wert aufsummieren (Gesamtplätze), (3) mit Bedingung zählen (Leichtflugzeuge). Gut für Akkumulator- und Zählschleifen an Objektfeldern.'
    ),
    (
        'c9000000-0000-0000-0000-000000000004',
        'b1000000-0000-0000-0000-000000000001',
        'java',
        'Vereinsverwaltung – Mitglieder-Array und Vereinsheim',
        $java$// ===== MITGLIED.java =====
public class MITGLIED{
    //Attribute
    String nachname;
    String vorname;
    int alter;
    boolean istAktiv;
    
    //Konstruktor
    public MITGLIED(String nachnameEingabe, String vornameEingabe, int alterEingabe, boolean istAktivEingabe){
        nachname = nachnameEingabe;
        vorname = vornameEingabe;
        alter = alterEingabe;
        istAktiv = istAktivEingabe;
    }
    
    public void nachnameAendern(String nachnameEingabe){
        nachname = nachnameEingabe;
    }
    
    public String nachnameZurueckgeben(){
        return nachname;
    }
    
    public void vornameAendern(String vornameEingabe){
        vorname = vornameEingabe;
    }
    
    public String vornameZurueckgeben(){
        return vorname;
    }
    
    public void alterAendern(int alterEingabe){
        alter = alterEingabe;
    }
    
    public int alterZurueckgeben(){
        return alter;
    }
    
    public void istAktivAendern(boolean istAktivEingabe){
        istAktiv = istAktivEingabe;
    }
    
    public boolean istAktivZurueckgeben(){
        return istAktiv;
    }
}

// ===== VEREINSHEIM.java =====
public class VEREINSHEIM{
    //Attribute
    int flaeche;
    int baujahr;
    boolean istOffen;
    
    
    public VEREINSHEIM(int flaecheEingabe, int baujahrEingabe){
        flaeche = flaecheEingabe;
        baujahr = baujahrEingabe;
        istOffen = false;
    }
    
    //Getter- und Setter-Methoden
    public void flaecheAendern(int flaecheEingabe){
        flaeche = flaecheEingabe;
    }
    
    public int flaecheZurueckgeben(){
        return flaeche;
    }
    
    public void baujahrAendern(int baujahrEingabe){
        baujahr = baujahrEingabe;
    }
    
    public int baujahrZurueckgeben(){
        return baujahr;
    }
    
    public void istOffenAendern(boolean istOffenEingabe){
        istOffen = istOffenEingabe;
    }
    
    public boolean istOffenZurueckgeben(){
        return istOffen;
    }
}

// ===== VEREIN.java =====
public class VEREIN{
    //Referenzattribut für den Zugriff auf die Mitglieder
    MITGLIED [] meineMitglieder;
    
    //Referenzattribut für den Zugriff auf das Vereinsheim
    VEREINSHEIM meinVereinsheim;
    
    //“normale“ Attribute
    String vereinsname;
    int mitgliederanzahl;
    String sportart;

    //Konstruktor
    public VEREIN(VEREINSHEIM vereinsheimEingabe, String vereinsnameEingabe, String sportartEingabe){
        //Erzeugung und Zuweisung eines neuen Arrays für (maximal 1000) Objekte der Klasse MITGLIED
        meineMitglieder = new MITGLIED[1000];
        //Zuweisung des VEREINSHEIM-Objekts
        meinVereinsheim = vereinsheimEingabe;
        
        //Zuweisung der "normalen" Attribute
        vereinsname = vereinsnameEingabe;
        sportart = sportartEingabe;
        mitgliederanzahl = 0;
    }

    //Methoden
    public void neuesMitgliedAufnehmen(MITGLIED mitgliedNeu){
        meineMitglieder[mitgliederanzahl] = mitgliedNeu;
        mitgliederanzahl = mitgliederanzahl + 1;
    }
    
    public void mitgliederInfosAktiveAusgeben(){
        System.out.println("Der Verein hat aktuell die folgenden aktiven Mitglieder:");
        for(int i=0; i<mitgliederanzahl; i=i+1){
            MITGLIED geradeBetrachtetesMitglied = meineMitglieder[i];
            if(geradeBetrachtetesMitglied.istAktivZurueckgeben()==true){
                System.out.println("Mitglied Nummer " + i+1 + ":");
                System.out.println(geradeBetrachtetesMitglied.nachnameZurueckgeben() + " " 
                                    + geradeBetrachtetesMitglied.vornameZurueckgeben() + " "
                                    + "(" + geradeBetrachtetesMitglied.alterZurueckgeben() + ")");
            }
        }
    }
    
    //ZUSATZAUFGABE 1
    public boolean gibtEsEinMitgliedMitNamen(String nameEingabe){
        for(int i=0; i<mitgliederanzahl; i=i+1){
             MITGLIED geradeBetrachtetesMitglied = meineMitglieder[i];
             if(geradeBetrachtetesMitglied.nachnameZurueckgeben().equals(nameEingabe)){
                 System.out.println(geradeBetrachtetesMitglied.nachnameZurueckgeben() + " " 
                    + geradeBetrachtetesMitglied.vornameZurueckgeben() + " "
                    + "(" + geradeBetrachtetesMitglied.alterZurueckgeben() + ")");
                    //return-Statement bricht Methode ab und das ist hier auch gut so...
                    return true;
             }
        }
        return false;
    }
    
    //ZUSATZAUFGABE 2
    //Je nachdem, ob gerade offen oder zu ist, soll das Vereinsheim zu- oder aufgesperrt werden.
    public void vereinsheimAufOderZu(){
        if(meinVereinsheim.istOffenZurueckgeben()==true){
            meinVereinsheim.istOffenAendern(false);
            System.out.println("Das Vereinsheim wurde nun zugesperrt!");
        }else{
            meinVereinsheim.istOffenAendern(true);
            System.out.println("Das Vereinsheim wurde nun aufgesperrt!");
        }   
    }
   
    //Getter- und Setter-Methoden: hier der Übersichtlichkeit halber mal weggelassen...
    
}
$java$,
        'Ein VEREIN verwaltet seine Mitglieder in einem Array von MITGLIED-Objekten und besitzt eine Referenz auf ein VEREINSHEIM. Schreibe Methoden, um Mitglieder aufzunehmen, die Infos aller aktiven Mitglieder auszugeben, per linearer Suche zu prüfen, ob es ein Mitglied mit einem bestimmten Nachnamen gibt, und das Vereinsheim je nach Zustand auf- oder zuzusperren.',
        'hard',
        'Kombiniert Array von Objekten mit einer zweiten Assoziation (VEREINSHEIM) und linearer Suche mit vorzeitigem return. Betont: Filtern nach booleschem Attribut (aktive Mitglieder), lineare Suche mit equals(), Zustandswechsel über boolesches Attribut. Enthält Zusatzaufgaben.'
    )
ON CONFLICT (id) DO UPDATE SET
    topic_id          = EXCLUDED.topic_id,
    language          = EXCLUDED.language,
    title             = EXCLUDED.title,
    code              = EXCLUDED.code,
    problem_statement = EXCLUDED.problem_statement,
    difficulty        = EXCLUDED.difficulty,
    notes             = EXCLUDED.notes;

COMMIT;

-- Verify:
--   SELECT title, topic_id, difficulty, length(code) AS code_len
--   FROM curriculum_code_examples
--   WHERE id LIKE 'c9000000-%' ORDER BY id;
