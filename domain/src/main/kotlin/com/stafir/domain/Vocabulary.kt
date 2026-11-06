package com.stafir.domain

/**
 * Un "concetto" ricercabile in italiano (es. "mare") collegato alle etichette
 * prodotte dai riconoscitori di immagini (es. "beach", "sea", "ocean").
 * È il ponte tra le parole italiane dell'utente e ciò che i modelli vedono.
 */
data class Concept(
    val id: String,
    val italianName: String,
    /** Parole italiane (singolari, plurali, sinonimi) che attivano il concetto. */
    val synonyms: Set<String>,
    /** Etichette (in minuscolo) dei riconoscitori che corrispondono al concetto. */
    val labels: Set<String>,
    val emoji: String = "",
)

object Vocabulary {

    private fun c(id: String, name: String, emoji: String, syn: String, labels: String) = Concept(
        id = id,
        italianName = name,
        synonyms = syn.split(',').map { TextNormalizer.normalize(it) }.filter { it.isNotBlank() }.toSet() +
            TextNormalizer.normalize(name),
        labels = labels.split(',').map { it.trim().lowercase() }.filter { it.isNotBlank() }.toSet(),
        emoji = emoji,
    )

    /** Categorie di scene e soggetti disponibili in Fase 1. */
    val concepts: List<Concept> = listOf(
        c("sea", "Mare", "🌊", "mare,mari,spiaggia,spiagge,costa,oceano,lido,scogli,scogliera,onde",
            "beach,sea,ocean,coast,wave,waves,surfing,shore,sand,lagoon,pier,swimwear,bikini"),
        c("mountain", "Montagna", "⛰️", "montagna,montagne,monti,monte,alpi,dolomiti,vetta,cima,escursione,trekking,rifugio",
            "mountain,hill,cliff,glacier,rock,alps,hiking,climbing,valley,himalayan"),
        c("snow", "Neve", "❄️", "neve,nevicata,innevato,innevata,sci,sciare,inverno,ghiaccio",
            "snow,skiing,ski,snowboarding,ice,iceberg,glacier,sled,winter"),
        c("forest", "Bosco e natura", "🌲", "bosco,boschi,foresta,foreste,natura,alberi,albero,prato,prati,campagna,parco",
            "forest,tree,trees,woodland,jungle,plant,grass,park,garden,leaf,moss,field,vegetation,nature"),
        c("lake", "Lago e fiume", "🏞️", "lago,laghi,fiume,fiumi,cascata,cascate,torrente",
            "lake,river,waterfall,stream,pond,canal"),
        c("sunset", "Tramonto e cielo", "🌅", "tramonto,tramonti,alba,albe,cielo,nuvole,arcobaleno",
            "sunset,sunrise,sky,cloud,clouds,rainbow,dusk,horizon"),
        c("city", "Città", "🏙️", "citta,centro,strada,strade,piazza,piazze,palazzo,palazzi,grattacielo,grattacieli,monumento,monumenti,chiesa,chiese,ponte,ponti,castello",
            "city,building,skyscraper,tower,street,road,bridge,church,cathedral,temple,castle,palace,monument,statue,town,architecture,downtown,fountain,plaza"),
        c("indoor", "Interni", "🏠", "interni,casa,camera,cucina,salotto,stanza,ufficio",
            "room,kitchen,bedroom,living room,furniture,couch,sink,shelf,interior design,office,bathroom,table,chair,desk"),
        c("food", "Cibo", "🍝", "cibo,piatto,piatti,pranzo,cena,colazione,pizza,pasta,dolce,dolci,torta,gelato,mangiare,aperitivo",
            "food,dish,cuisine,pizza,pasta,cake,dessert,bread,meal,lunch,dinner,breakfast,restaurant,vegetable,fruit,sushi,salad,cheeseburger,hot dog,sandwich,ice cream,cookie,bento,soup,noodle,meat,seafood,baked goods,fast food"),
        c("drink", "Bevande", "🍷", "vino,birra,caffe,cocktail,drink,bevanda,bevande,bicchiere",
            "wine,beer,coffee,cocktail,drink,juice,cup,tea,bottle,alcohol,wine glass"),
        c("animals", "Animali", "🐾", "animale,animali,cane,cani,gatto,gatti,cavallo,cavalli,uccello,uccelli,pesce,pesci,mucca",
            "dog,cat,horse,bird,fish,cow,animal,pet,sheep,duck,butterfly,insect,rabbit,bear,lion,elephant,monkey,zoo,aquarium,dinosaur,reptile,turtle,squirrel"),
        c("dog", "Cani", "🐶", "cane,cani,cagnolino,cucciolo",
            "dog,puppy,pet"),
        c("cat", "Gatti", "🐱", "gatto,gatti,gattino,micio",
            "cat,kitten"),
        c("people", "Persone", "🧑", "persona,persone,gente,ritratto,ritratti,gruppo,amici,famiglia,bambino,bambini,bimbo,bimbi,neonato",
            "person,people,smile,selfie,baby,child,crowd,team,beard,moustache,hand,ear,eyelash,sitting,standing,fun,portrait,face"),
        c("selfie", "Selfie", "🤳", "selfie,autoscatto",
            "selfie"),
        c("events", "Eventi e feste", "🎉", "festa,feste,evento,eventi,compleanno,matrimonio,matrimoni,concerto,concerti,natale,capodanno,laurea,cerimonia,party,discoteca",
            "party,event,wedding,concert,birthday,christmas,fireworks,balloon,graduation,prom,dance,carnival,musical instrument,musician,stage,bonfire,circus,cake"),
        c("sport", "Sport", "⚽", "sport,calcio,partita,stadio,palestra,bici,bicicletta,corsa,nuoto,piscina,tennis",
            "sport,stadium,football,soccer,swimming pool,swimming,bicycle,cycling,running,tennis,skateboard,surfing,skiing,rafting,gym,basketball,racket,team"),
        c("vehicles", "Veicoli e viaggi", "🚗", "auto,macchina,macchine,moto,treno,treni,aereo,aerei,barca,barche,nave,traghetto,bus,autobus,viaggio,viaggi,aeroporto",
            "car,vehicle,motorcycle,train,airplane,aircraft,boat,ship,bus,truck,bicycle,tractor,sailboat,yacht,jet ski,airport,wheel,helicopter,shipwreck"),
        c("flowers", "Fiori e piante", "🌸", "fiore,fiori,pianta,piante,giardino,giardini,rosa,rose",
            "flower,plant,garden,petal,rose,houseplant,flowerpot"),
        c("documents", "Documenti", "📄", "documento,documenti,carta,fogli,foglio,lettera,contratto,ricevuta,ricevute,scontrino,scontrini,fattura,fatture,biglietto,biglietti,etichetta,etichette,menu,testo,pagina,libro,libri",
            "paper,document,receipt,text,menu,poster,newspaper,book,handwriting,font,letter,ticket,page,label,business card,brochure,comics,notebook"),
        c("screens", "Schermi e screenshot", "📱", "screenshot,schermata,schermate,schermo,monitor,computer,telefono",
            "screenshot,monitor,computer,screen,display device,mobile phone,laptop,television,electronic device"),
        c("night", "Notte", "🌙", "notte,notturna,notturne,sera,stelle,luna,luci",
            "night,moon,star,stars,darkness,lights,neon,light"),
    )

    private val bySynonym: Map<String, Concept> = buildMap {
        // I concetti più specifici (cani, gatti, selfie, neve) vincono su quelli generali.
        concepts.sortedBy { it.synonyms.size }.reversed().forEach { concept ->
            concept.synonyms.forEach { put(it, concept) }
        }
        concepts.filter { it.id in setOf("dog", "cat", "selfie", "snow", "drink") }.forEach { concept ->
            concept.synonyms.forEach { put(it, concept) }
        }
    }

    fun conceptForWord(normalizedWord: String): Concept? = bySynonym[normalizedWord]

    /** Categorie implicite: un cane è anche un animale, un selfie ritrae anche persone. */
    private val implied: Map<String, Set<String>> = mapOf(
        "dog" to setOf("animals"),
        "cat" to setOf("animals"),
        "selfie" to setOf("people"),
    )

    /** Aggiunge le categorie implicite (usato per le etichette fatte a mano del banco qualità). */
    fun withImplied(ids: Set<String>): Set<String> = ids + ids.flatMap { implied[it].orEmpty() }

    fun conceptById(id: String): Concept? = concepts.firstOrNull { it.id == id }

    /** Concetti attivati da un insieme di etichette (per mostrare le categorie di una foto). */
    fun conceptsForLabels(labels: Collection<String>): List<Concept> {
        val set = labels.map { it.lowercase() }.toSet()
        return concepts.filter { c -> c.labels.any { it in set } }
    }

    /** Traduzione italiana delle etichette più comuni dei riconoscitori. */
    private val translations: Map<String, String> = mapOf(
        "beach" to "spiaggia", "sea" to "mare", "ocean" to "oceano", "coast" to "costa", "wave" to "onda",
        "sand" to "sabbia", "mountain" to "montagna", "hill" to "collina", "cliff" to "scogliera",
        "glacier" to "ghiacciaio", "rock" to "roccia", "snow" to "neve", "ice" to "ghiaccio",
        "skiing" to "sci", "forest" to "bosco", "tree" to "albero", "plant" to "pianta", "grass" to "erba",
        "park" to "parco", "garden" to "giardino", "flower" to "fiore", "petal" to "petalo", "leaf" to "foglia",
        "lake" to "lago", "river" to "fiume", "waterfall" to "cascata", "sky" to "cielo", "cloud" to "nuvola",
        "sunset" to "tramonto", "sunrise" to "alba", "rainbow" to "arcobaleno", "city" to "città",
        "building" to "edificio", "skyscraper" to "grattacielo", "tower" to "torre", "street" to "strada",
        "road" to "strada", "bridge" to "ponte", "church" to "chiesa", "temple" to "tempio", "castle" to "castello",
        "monument" to "monumento", "statue" to "statua", "fountain" to "fontana", "room" to "stanza",
        "kitchen" to "cucina", "furniture" to "mobili", "couch" to "divano", "table" to "tavolo", "chair" to "sedia",
        "desk" to "scrivania", "shelf" to "scaffale", "food" to "cibo", "dish" to "piatto", "cuisine" to "cucina",
        "pizza" to "pizza", "pasta" to "pasta", "cake" to "torta", "dessert" to "dolce", "bread" to "pane",
        "meal" to "pasto", "vegetable" to "verdura", "fruit" to "frutta", "sushi" to "sushi", "salad" to "insalata",
        "ice cream" to "gelato", "cookie" to "biscotto", "soup" to "zuppa", "meat" to "carne", "seafood" to "frutti di mare",
        "wine" to "vino", "beer" to "birra", "coffee" to "caffè", "cocktail" to "cocktail", "drink" to "bevanda",
        "juice" to "succo", "cup" to "tazza", "bottle" to "bottiglia", "tea" to "tè", "dog" to "cane", "cat" to "gatto",
        "horse" to "cavallo", "bird" to "uccello", "fish" to "pesce", "cow" to "mucca", "sheep" to "pecora",
        "duck" to "anatra", "butterfly" to "farfalla", "insect" to "insetto", "pet" to "animale domestico",
        "person" to "persona", "smile" to "sorriso", "selfie" to "selfie", "baby" to "neonato", "child" to "bambino",
        "crowd" to "folla", "team" to "squadra", "beard" to "barba", "hand" to "mano", "fun" to "divertimento",
        "party" to "festa", "event" to "evento", "wedding" to "matrimonio", "concert" to "concerto",
        "birthday" to "compleanno", "christmas" to "natale", "fireworks" to "fuochi d'artificio",
        "balloon" to "palloncino", "graduation" to "laurea", "dance" to "ballo", "musical instrument" to "strumento musicale",
        "musician" to "musicista", "stadium" to "stadio", "swimming pool" to "piscina", "bicycle" to "bicicletta",
        "car" to "auto", "vehicle" to "veicolo", "motorcycle" to "moto", "train" to "treno", "airplane" to "aereo",
        "boat" to "barca", "ship" to "nave", "bus" to "autobus", "truck" to "camion", "paper" to "carta",
        "document" to "documento", "receipt" to "scontrino", "text" to "testo", "menu" to "menu", "poster" to "manifesto",
        "newspaper" to "giornale", "book" to "libro", "handwriting" to "scrittura a mano", "ticket" to "biglietto",
        "screenshot" to "screenshot", "monitor" to "monitor", "computer" to "computer", "mobile phone" to "telefono",
        "laptop" to "portatile", "television" to "televisione", "night" to "notte", "moon" to "luna", "star" to "stella",
        "light" to "luce", "toy" to "giocattolo", "sitting" to "seduto", "jacket" to "giacca", "shoe" to "scarpa",
        "glasses" to "occhiali", "sunglasses" to "occhiali da sole", "hat" to "cappello", "flag" to "bandiera",
        "umbrella" to "ombrello", "bag" to "borsa", "pool" to "piscina", "swimwear" to "costume da bagno",
        "camping" to "campeggio", "tent" to "tenda", "bonfire" to "falò", "desert" to "deserto", "cave" to "grotta",
        "aquarium" to "acquario", "zoo" to "zoo", "circus" to "circo", "museum" to "museo", "font" to "testo",
        "pier" to "molo", "lagoon" to "laguna", "boardwalk" to "lungomare", "shore" to "riva", "net" to "rete",
        "cool" to "bello", "musical" to "musicale", "stage" to "palco", "sports" to "sport", "sport" to "sport",
        "leisure" to "tempo libero", "vacation" to "vacanza", "basset hound" to "bassotto", "boxer" to "boxer",
        "cathedral" to "cattedrale", "competition" to "gara", "eyelash" to "ciglia", "jeans" to "jeans",
        "metal" to "metallo", "palace" to "palazzo", "architecture" to "architettura", "town" to "paese", "downtown" to "centro città", "plaza" to "piazza",
        "lunch" to "pranzo", "dinner" to "cena", "breakfast" to "colazione", "restaurant" to "ristorante",
        "baked goods" to "dolci da forno", "fast food" to "fast food", "sandwich" to "panino", "noodle" to "spaghetti",
        "puppy" to "cucciolo", "kitten" to "gattino", "rabbit" to "coniglio", "bear" to "orso", "lion" to "leone",
        "elephant" to "elefante", "monkey" to "scimmia", "turtle" to "tartaruga", "squirrel" to "scoiattolo",
        "flowerpot" to "vaso di fiori", "houseplant" to "pianta da appartamento", "rose" to "rosa",
        "football" to "calcio", "soccer" to "calcio", "swimming" to "nuoto", "running" to "corsa", "tennis" to "tennis",
        "skateboard" to "skateboard", "surfing" to "surf", "rafting" to "rafting", "gym" to "palestra",
        "basketball" to "basket", "racket" to "racchetta", "snowboarding" to "snowboard", "sled" to "slittino",
        "iceberg" to "iceberg", "hiking" to "escursionismo", "climbing" to "arrampicata", "valley" to "valle",
        "field" to "prato", "jungle" to "giungla", "moss" to "muschio", "stream" to "ruscello", "pond" to "stagno",
        "canal" to "canale", "dusk" to "crepuscolo", "horizon" to "orizzonte", "bedroom" to "camera da letto",
        "living room" to "soggiorno", "bathroom" to "bagno", "office" to "ufficio", "interior design" to "arredamento",
        "sink" to "lavandino", "carnival" to "carnevale", "prom" to "ballo", "dress" to "vestito",
        "letter" to "lettera", "page" to "pagina", "label" to "etichetta", "business card" to "biglietto da visita",
        "brochure" to "opuscolo", "comics" to "fumetti", "notebook" to "quaderno", "display device" to "schermo",
        "electronic device" to "dispositivo elettronico", "screen" to "schermo", "darkness" to "buio", "lights" to "luci",
        "neon" to "neon", "aircraft" to "aereo", "sailboat" to "barca a vela", "yacht" to "yacht", "jet ski" to "moto d'acqua",
        "airport" to "aeroporto", "wheel" to "ruota", "helicopter" to "elicottero", "tractor" to "trattore",
        "shipwreck" to "relitto", "hair" to "capelli", "ear" to "orecchio", "nail" to "unghia",
        "standing" to "in piedi", "pattern" to "motivo", "flesh" to "pelle", "denim" to "jeans", "superhero" to "supereroe",
    )

    fun italianLabel(label: String): String = translations[label.lowercase()] ?: label.lowercase()
}
