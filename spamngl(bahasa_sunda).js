(async () => {
    const TARGET_USERNAME = "YOUR_NGL_USERNAME";
    const JUMLAH_PESAN = 20;
    const DELAY_MIN = 500;
    const DELAY_MAX = 1000;

    let username = TARGET_USERNAME;
    if (!username) {
        const urlPath = window.location.pathname.replace("/", "");
        if (!urlPath) return;
        username = urlPath;
    }

    const TEMPLATES = [
        "haoeun",
        "haoeun pisan",
        "punten",
        "kumaha damang",
        "kumaha eta",
        "kumaha kabare",
        "kabar na kumaha",
        "damang haoeun",
        "sehat haoeun",
        "cageur haoeun",
        "teu kumaha damang",
        "alhamdulillah cageur",
        "atuh na kumaha",
        "hayu geura",
        "hayu atuh",
        "geura atuh",
        "sok atuh",
        "kunaon atuh",
        "naon kunaon",
        "naon eta",
        "naon sih",
        "naon wae",
        "moal ka ditu",
        "moal bae",
        "teu apal",
        "poho kana",
        "poho henteu apal",
        "teu terang",
        "teu jelas",
        "teu nyambung",
        "teu ngarana",
        "entah ah",
        "sok pilih",
        "sok tuluy",
        "sok indukan",
        "geura tuluy",
        "tuluy atuh",
        "nu mana",
        "nu mana eta",
        "di mana wae",
        "kapan wae",
        "sabaraha",
        "sabaraha eta",
        "raos pisan",
        "enak pisan",
        "pinter pisan",
        "pandai pisan",
        "cakep pisan",
        "alus pisan",
        "endah pisan",
        "munggar pisan",
        "aya naon",
        "aya eta",
        "teu aya naon",
        "henteu aya",
        "sok ngobrol",
        "ngobrol atuh",
        "hayu ngobrol",
        "hayu sare",
        "sare atuh",
        "geura sare",
        "dahar atuh",
        "geura dahar",
        "haoeun daharna",
        "dahar geus",
        "geus makan acan",
        "acan dahar",
        "acan sare",
        "acan mandi",
        "geura mandi",
        "mandi atuh",
        "toe atuh",
        "toe geura",
        "kumaha toe",
        "toe na kumaha",
        "terus naon",
        "terus kumaha",
        "lajeng naon",
        "lajeng kumaha",
        "cenah naon",
        "cenah kitu",
        "cenah kitu teh",
        "kumaha kabare ayeuna damang",
        "hare panas pisan enya henteu",
        "hujan terus ieu moal beres beres",
        "pageurna handeueul pisan",
        "isuk geus berangkat acan henteu",
        "teu apal kana eta geus lila",
        "poho kana nami na naon eta",
        "sok tuluy atuh moal ngadug",
        "geura bae moal bawa lama",
        "hayu sare geus peuting wae",
        "dahar geus acan atuh hayu",
        "geus peuting kunaon acan sare",
        "acan pasrah eta kunaon atuh",
        "raos pisan teh naon eta",
        "enak pisan hayu geura nyoba",
        "kumaha eta naon kunaon sih",
        "toe na kumaha ayeuna damang",
        "lajeng naon eta teh kumaha",
        "cenah kitu teh bener henteu",
        "sok indit atuh moal bawa lama",
        "geura balik atuh geus malem",
        "haoeun ayeuna geus peuting wae",
        "sehat henteu ayeuna kumaha",
        "geus lila teu papanggih damang",
        "hayu papanggih deui kapan",
        "kapan hayu ngobrol deui",
        "raos atuh euy enak pisan",
        "maneh na kumaha ayeuna",
        "anak na geus badag acan henteu",
        "teu apal abdi kana eta",
        "kumaha sih",
        "naon sih eta",
        "naon kunaon",
        "kunaon eta sih",
        "di mana wae sih",
        "kapan wae sih",
        "sabaraha sih",
        "saha eta sih",
        "nu saha sih",
        "kenapa sih",
        "naha sih",
        "naha eta",
        "naha kitu",
        "naha moal",
        "naha teu daek",
        "daek henteu sih",
        "bisi henteu",
        "bisi aya",
        "bisi tiasa",
        "tiasa henteu",
        "tiasa teu",
        "mung tiasa kitu wae",
        "teu tiasa naon deui",
        "henteu kahaja",
        "kahaja eta",
        "sengaja henteu",
        "sengaja eta",
        "bener henteu sih",
        "bener teu eta",
        "jelas henteu",
        "jelas teu sih",
        "terang henteu",
        "punten naon eta",
        "euy",
        "atuh euy",
        "sok atuh euy",
        "geura euy",
        "hayu euy",
        "naon euy",
        "kunaon euy",
        "toe euy",
        "kumaha euy",
        "ceuk na euy",
        "ceuk eta euy",
        "maha euy",
        "sakit euy",
        "babari euy",
        "reueus euy",
        "reueus pisan euy",
        "jengkeng euy",
        "ngalak euy",
        "ngalak pisan euy",
        "beudeuy euy",
        "beudeuy pisan euy",
        "uliing euy",
        "uliing pisan euy",
        "ngabul euy",
        "ngabul pisan euy",
        "ngahujang euy",
        "ngahujang pisan euy",
        "buang euy",
        "eungap euy",
        "geli euy",
        "nggili euy",
        "nggili pisan euy",
        "aiseh euy",
        "aiseh pisan euy",
        "weh euy",
        "weh pisan euy",
        "cagedik euy",
        "ngagedik euy",
        "caang euy",
        "poek euy",
        "haneut euy",
        "tiris euy",
        "jeung euy",
        "sareeung euy",
        "euy naon kunaon eta sih",
        "euy geura atuh moal bawa lama",
        "hayu euy sok tuluy moal ngadug",
        "toe euy kumaha ayeuna",
        "naon euy eta teh kunaon sih",
        "euy ceuk na eta bener henteu",
        "sok atuh euy moal diajar",
        "geura bae euy moal ngahujang",
        "raos pisan euy naon eta",
        "enak pisan euy hayu nyoba",
        "euy ayeuna kumaha toe na",
        "sok tuluy euy geus lila wae",
        "hayu euy geura sare geus peuting",
        "euy dahar atuh geus lila teu dahar",
        "naon sih euy moal jelas",
        "kumaha euy geus cageur acan",
        "euy geus badag wae sih",
        "sok papanggih euy geus lila",
        "euy naon eta teh raos pisan",
        "geura euy hayu indit bareng",
        "punten abdi badag",
        "sami sami",
        "hatur nuhun",
        "hatur nuhun pisan",
        "muhun",
        "sakedap",
        "sakedap pisan",
        "sehat haoeun",
        "kumaha kabarna",
        "kumaha padaharna",
        "damang haoeun",
        "alhamdulillah",
        "tiasa henteu",
        "tiasa abdi",
        "tiasa henteu abdi",
        "punten abdi teu ngarti",
        "punten abdi teu apal",
        "abdi henteu terang",
        "punten abdi nunggu",
        "mohon hurungna",
        "sumangga",
        "sumangga abdi",
        "sakit euy hati na",
        "sedih pisan euy ayeuna",
        "reueus pisan pikiran na",
        "jengkeng euy kana eta",
        "ngahujang euy kana naon",
        "beudeuy euy rasana",
        "raos euy tapi teu tiasa",
        "enak euy tapi teu bisi",
        "poho euy kana eta tapi teu bisa",
        "ngagoler euy hate na",
        "geli euy rasana kana eta",
        "nggili euy tapi teu bisa ngaluarkeun",
        "aiseh euy rasana teu karuan",
        "ngalak euy hati na kana eta",
        "uliing euy pikiran teu pegel",
        "raos pisan tapi moal bisi",
        "sakit euy tapi teu ningali",
        "reueus euy tapi teu bisa ngomong",
        "beudeuy euy tapi teu ada naon naon",
        "ngabul euy rasana kana eta",
        "sok pinter euy",
        "sok hebat euy",
        "sok luhur euy",
        "sok wibawa euy",
        "ceuk na sok ngarti euy",
        "sok ngajar euy",
        "sok bener euy",
        "sok hade euy",
        "sok alus euy",
        "sok cageur euy",
        "sok sempurna euy",
        "luhur pisan euy ceuk na",
        "hebat pisan euy ceuk na",
        "pinter pisan euy sok ngarti",
        "hade pisan euy sok bener",
        "sok nyuhunkeun euy",
        "sok boga hak euy",
        "sok leupas euy",
        "sok bersih euy",
        "sok suci euy",
        "damang",
        "array",
        "mah",
        "naon mah",
        "kumaha mah",
        "eta mah",
        "ceuk mah",
        "toe mah",
        "na kumaha mah",
        "nu mah",
        "saha mah",
        "saha sih mah",
        "ceuk na mah",
        "ceuk eta mah",
        "kunaon mah",
        "naon sih mah",
        "toe sih mah",
        "kumaha sih mah",
        "weh naon eta",
        "toe naon sih",
        "na weh eta",
        "euy na eta",
        "mah eta teh",
        "naon teh eta",
        "kunaon teh eta",
        "kumaha teh ayeuna",
        "geus acan teh",
        "acan geus teh",
        "moal geus teh",
        "daek moal teh",
        "bener teh henteu",
        "henteu teh bener",
        "enya teh enya",
        "henteu teh henteu",
        "sok teh sok",
        "atuh teh atuh",
        "geura teh geura",
        "hayu teh hayu"
    ];

    const TYPO_MAP = {
        "kumaha": ["kumaha", "kumah", "kmaha", "kumh"],
        "naon": ["naon", "naun", "non", "nawn"],
        "kunaon": ["kunaon", "kunon", "knaon"],
        "henteu": ["henteu", "hnteu", "nteu"],
        "teu": ["teu", "tu", "t"],
        "atuh": ["atuh", "ath", "tuh", "uh"],
        "euy": ["euy", "ey", "euyy", "euyyy", "uy"],
        "pisan": ["pisan", "psan", "pisn"],
        "geura": ["geura", "gra", "gera", "gura"],
        "hayu": ["hayu", "hay", "hyu", "ayu"],
        "sok": ["sok", "sk", "soek"],
        "tuluy": ["tuluy", "tuly", "tluy"],
        "damang": ["damang", "dmang", "dang"],
        "haoeun": ["haoeun", "haen", "haeun", "hoen"],
        "cageur": ["cageur", "cagr", "cagur", "cgeur"],
        "raos": ["raos", "ros", "raus"],
        "enak": ["enak", "nak", "ena"],
        "pinter": ["pinter", "pitr", "pintr"],
        "cakep": ["cakep", "cakp", "cekp", "caep"],
        "alus": ["alus", "als"],
        "endah": ["endah", "endh"],
        "sabaraha": ["sabaraha", "sbaraha", "sabhrah"],
        "punten": ["punten", "puntne", "pnten", "puntrn"],
        "hatur": ["hatur", "hatr", "htr"],
        "nuhun": ["nuhun", "nhun", "nun"],
        "sakedap": ["sakedap", "skedap", "skdap"],
        "sumangga": ["sumangga", "smangga", "sumnga"],
        "abdi": ["abdi", "abd", "abi", "adi"],
        "maneh": ["maneh", "manh", "mneh", "mnh"],
        "maha": ["maha", "mha", "mah"],
        "reueus": ["reueus", "reus", "rues", "reueuss"],
        "jengkeng": ["jengkeng", "jngkeng", "jengkng"],
        "ngalak": ["ngalak", "nglak", "ngalakk"],
        "beudeuy": ["beudeuy", "beudy", "beudey"],
        "uliing": ["uliing", "uling", "ulng"],
        "ngabul": ["ngabul", "ngbl", "ngabl"],
        "ngahujang": ["ngahujang", "ngujang", "ngahujng"],
        "nggili": ["nggili", "nggli", "ggili"],
        "aiseh": ["aiseh", "aisehh", "aise"],
        "sengaja": ["sengaja", "sngaja", "sengj"],
        "kahaja": ["kahaja", "kahj", "khja"],
        "cenah": ["cenah", "cnah", "cna"],
        "lajeng": ["lajeng", "ljeng", "lajng"],
        "sareeung": ["sareeung", "sreung", "sareung"],
        "ngagoler": ["ngagoler", "nggoler", "ngagolr"],
        "ngebrek": ["ngebrek", "ngbrek", "ngebrk"]
    };

    function randInt(min, max) {
        return Math.floor(Math.random() * (max - min + 1)) + min;
    }

    function randomPick(arr) {
        return arr[Math.floor(Math.random() * arr.length)];
    }

    function sleep(ms) {
        return new Promise(resolve => setTimeout(resolve, ms));
    }

    function humanize(text) {
        let result = text;

        for (const [kataAsli, variasi] of Object.entries(TYPO_MAP)) {
            if (result.includes(kataAsli) && Math.random() > 0.35) {
                result = result.replace(kataAsli, randomPick(variasi));
            }
        }

        if (Math.random() < 0.15) {
            result = result.toLowerCase();
        }

        if (Math.random() < 0.12) {
            const words = result.split(" ");
            if (words.length > 2) {
                const idx = randInt(1, words.length - 2);
                words[idx] = words[idx] + "  ";
                result = words.join(" ");
            }
        }

        if (Math.random() < 0.1) {
            result = result.replace(/\?\s*$/, "");
        }

        if (Math.random() < 0.15) {
            result = result + "...";
        }

        if (Math.random() < 0.1 && !result.endsWith("mah") && result.length > 5) {
            result = result + " mah";
        }

        if (Math.random() < 0.1 && !result.endsWith("euy") && result.length > 5) {
            result = result + " euy";
        }

        if (Math.random() < 0.08 && !result.endsWith("atuh") && result.length > 5) {
            result = result + " atuh";
        }

        if (Math.random() < 0.1 && result.length > 3) {
            const lastChar = result.trim().slice(-1);
            if (lastChar.match(/[a-z]/i)) {
                result = result.trim() + lastChar;
            }
        }

        if (Math.random() < 0.08 && result.length > 5) {
            const filler = randomPick(["weh ", "na ", "eta ", "toe ", "mah ", "ceuk "]);
            result = filler + result;
        }

        return result;
    }

    function generateMessages(count) {
        const messages = new Set();
        let safety = 0;

        while (messages.size < count && safety < count * 10) {
            const base = randomPick(TEMPLATES);
            messages.add(humanize(base));
            safety++;
        }

        return Array.from(messages);
    }

    async function sendNgl(user, msg) {
        try {
            const res = await fetch("https://ngl.link/api/submit", {
                method: "POST",
                headers: {
                    "Content-Type": "application/x-www-form-urlencoded",
                    "Origin": "https://ngl.link",
                    "Referer": `https://ngl.link/${user}`
                },
                body: new URLSearchParams({
                    username: user,
                    question: msg,
                    deviceId: crypto.randomUUID ? crypto.randomUUID() : `${Date.now()}-${Math.random().toString(36).substr(2, 9)}`
                }).toString()
            });
            return res.ok;
        } catch (e) {
            return false;
        }
    }

    console.clear();
    console.log("%cNGL SENDER - BASA SUNDA", "color: #2ecc71; font-size: 16px; font-weight: bold;");
    console.log(`%cTarget : ${username}`, "color: #4ecdc4; font-size: 12px;");
    console.log(`%cJumlah : ${JUMLAH_PESAN} pesan`, "color: #4ecdc4; font-size: 12px;");
    console.log(`%cDelay  : ${DELAY_MIN/1000}s - ${DELAY_MAX/1000}s`, "color: #4ecdc4; font-size: 12px;");
    console.log("%c----------------------------------", "color: gray;");
    console.log("%cMulai NGL spam...", "color: yellow;");

    await sleep(3000);

    const messages = generateMessages(JUMLAH_PESAN);
    let berhasil = 0;
    let gagal = 0;

    for (let i = 0; i < messages.length; i++) {
        const pesan = messages[i];
        const status = await sendNgl(username, pesan);

        if (status) {
            berhasil++;
            console.log(`%c[${i + 1}/${messages.length}] ✅ "${pesan}"`, "color: #2ecc71;");
        } else {
            gagal++;
            console.log(`%c[${i + 1}/${messages.length}] ❌ "${pesan}"`, "color: #e74c3c;");
        }

        if (i < messages.length - 1) {
            const delay = randInt(DELAY_MIN, DELAY_MAX);
            console.log(`%c   ⏳ ${(delay / 1000).toFixed(1)}s...`, "color: gray;");
            await sleep(delay);
        }
    }

    console.log("%c----------------------------------", "color: gray;");
    console.log("%c📊 STATISTIK!", "color: #f1c40f; font-size: 14px; font-weight: bold;");
    console.log(`%c✅ ${berhasil} | ❌ ${gagal}`, "color: #4ecdc4; font-size: 12px;");
})();