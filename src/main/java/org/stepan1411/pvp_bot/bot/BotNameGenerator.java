package org.stepan1411.pvp_bot.bot;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class BotNameGenerator {
    
    private static final List<String> PREFIXES = new ArrayList<>();
    private static final List<String> SUFFIXES = new ArrayList<>();
    private static final Random random = new Random();
    

    private static final String SPECIAL_NAMES_URL = "https://stepan1411.github.io/pvp-bot-fabric/special_names.json";
    

    private static List<String> specialNames = new ArrayList<>();
    private static int spawnChance = 10;
    private static boolean specialNamesLoaded = false;
    private static long lastLoadTime = 0;
    private static final long RELOAD_INTERVAL = 60000;
    

    private static final Pattern CAMEL_CASE = Pattern.compile("([A-Z][a-z0-9]+)");
    

    private static final String[] BASE_NAMES = {
        "Gpminer17", "Fighthai", "Zampez", "RaxsiarekYT", "C0RZZ",
"ThatOnePvper", "MasterJai", "ItzTzumi", "NoFlat", "JollyMarlow",
"Flametex", "zVentit", "Way_Finder", "Desyphering", "driftrye",
"Fergusus", "xicz", "Terowww", "freejoqt", "Fastive",
"1Wenzy", "LEONALtheLION", "sweatgod", "TO_IP", "Nibbysaur",
"barbygrill", "DarkKnightAragon", "Marko5k", "Crozzor", "Hyorinmarru",
"Freekee_Fang", "AiroKun", "XTki4ler", "Dijyn", "SuperQd_",
"Falsedge_", "Kylaz", "orderh", "Bart_5505", "zivahlol",
"Glitch6779", "TigerTr1es", "Frosprite", "oddbull123", "u6vq",
"Arsle", "foffalo", "Intallact", "LongerBrick", "TimoBunn1",
"Legendarryy", "chubbyseals", "chalkyxv", "isolatiion", "kittygirlkana",
"A3hleyy", "orbzW", "TPGWill", "ramogil", "idiq",
"stouteds", "Critical1y", "STORMPLAY", "AngryVerity67", "Kiphez",
"new_note", "midnqt", "adamknaap", "Royaleee", "dwggobestlt1",
"Lefdy", "DE3MOndey", "janekv", "Owkp_", "Sheep55555",
"Sweqtyy", "_10s", "X_SUS", "qPhil", "MaksX2",
"WodaZlodowca", "TirthEz", "ShadowZeuss", "Hynzaz", "Zyrela",
"iPr0f", "qkyixz", "kaevri", "__icebear", "JsK3",
"RxplyyOnDrugs", "SenseiCole", "Basehunt", "ninorc15"
	"Triphon", "vooti2", "Waww_w", "abybi", "Ameliieee",
"BlvckWlf", "catgirldandan", "Conruptus", "Dexterical", "fuzionxd",
"Ggc69", "JACKS5ON", "kuraqpn", "MacePvPer", "OliverDoge1571",
"PishPeshOwnOwns", "PuppyboyKoga", "scariestvariable", "SourMosquito440", "str3xya_",
"SwaayAbusesMe", "TeamLemon", "TerrorOfSerbia", "VALORANTG0D", "Vilppe22",
"vtcp", "BerkAmaBerk", "BlakeHimself", "CorruptNoob", "cxkN_",
"Dexzq", "Do1fin", "EnvityXD", "Evr250", "Exqwy",
"Luka2222", "meteorinto", "NBClash", "nigerianboss9834", "PotReducer",
"PufferBucket", "Raxen987", "Reboww", "Repent6", "SEBBE961",
"Spxctra", "statifey", "szad", "zLightx", "1QA1",
"AbramusovichOMEN", "Alligation", "AngelicHannah", "Axifek", "AyanBhai",
"dclfin", "evalt", "MALDITOMIKE", "Nokutiho_", "Vapyd",
"winloading", "yhGhost", "75kmh", "7Bqtii", "7sped",
"AlexBaguette", "D4rkcombo", "deadramc", "freaklybob", "FrostyIcl",
"G6lm", "ImRedz", "MaxIstEinNugget", "mwff", "Nomyt_",
"Pomorze", "qLuxxy", "RoadToLt5", "skibidi_rabi", "Spacederoo",
"TinyJuu_", "YttbensamX2", "ZavyMC", "_iAlireza_",
"AquaCm_", "CBA100H666", "LighteningBAM", "messiahyate", "NyanKano",
"Owinglink", "pearlww", "prekambr", "qAlfsar", "SigmaSeeker",
"SquishyIDK", "Tfoue", "VaSm_", "VoidS1ar", "wx9",
"zMixey", "Awaken3", "hanz3x", "Harabaxy", "KaJP",
"littletonygamer", "MrSlimis", "qpic", "rvms", "sieqq",
"so2a", "sw1tz", "Willysmannen", "WOOHOOpotato", "xarehc",
"YetiKing_", "0Controll", "0uwk", "2rAx", "AlienOverdose",
"Austroyer", "AlexOinks", "BABYFIUFIUSMILK", "DsmpOwner", "Gravy58",
"HabachiHarry692", "Hissaelion", "iloveosilasghg", "kittypowerrrr", "Lucky_Vynnel",
"matboo23", "mqnhunt", "Nesill", "nightmfall", "OnlyXclusive",
"Peely455", "Punch2Bro", "PuppyBoyTsunami", "R1SOTE", "Rezarkk",
"StagnantStan", "Th3B3ni", "TimthePooper", "Turboplayzlmao", "Vesperetta",
"yPingu", "Artin9", "B0riskre", "blazikenlover", "bxdo99",
"ClipSec", "convirq", "dilLOL", "DysonV99", "Ethmic",
"Gpplesauce", "haxwo", "HotWireJr", "Ihateredsox", "Itsuki_nakanoo",
"j3nsenY", "KaguneGhoul", "KetchupClient", "LunarFrostbite", "MOGBYLIKO",
"OkLoWzi", "PearlV3", "poepvlekje_", "qseg", "RamonPvP",
"Ronite_", "Sudata", "Tedzki", "Tornyc", "Tranquish",
"Urixen", "w8rp", "YuviHD", "1pengy", "awspect3",
"bommbolat1117", "Dummmmyboi", "ezduck", "FlameScythed", "HOGLINRIDER",
        "AcalaAca", "ParallelPara", "HydraHdx", "DockDock", "PhalaPha",
        "PlasmPlm", "EdgewareEdg", "CentrifugeCfg", "KiltNetwork", "UniqueUnq",
        "QuartzQtz", "BitcountryBcn", "InterlayIntr", "EquilibriumEq", "BifrostBnc",
        "DarwiniaRing", "CrabNetwork", "LitentryLitr", "IntegriteeTee", "RobonomicsXrt",
        "SoraXor", "PolkadexPdex", "OriginTrailTrac", "ChainflipFlip", "ComposablePica",
        "PicassoPica", "ComposablCmpl", "BasmatiBsmt", "FrequencyFrq", "T0T0",
        "AutomataAtma", "MantaManta", "CalamariKma", "DolphinDol", "SubsocialSubx",
        "ZeitgeistZtg", "InvArchInv", "KpronKpr", "AltairAir", "ParallelHeiko",
        "KhalaPha", "BasiliskBsx", "SneakySnek", "ImbueImbu", "AmplitudeAmp",
        "PendulumPen", "CurioCgu", "T3rnT3rn", "BrainnetBrn", "AjunaAjun",
        "GenshiroGns", "OpalOpl", "GamifyGmf", "MythosMyth", "RoccocoRoc",
        "WestendWst", "VersiVrs", "LocalhostLoc", "TestnetTst", "DevnetDev",
        "StagenetStg", "CanaryCnr", "SandboxSbx", "PlaygroundPly", "DemoDem",
        "PreviewPrv", "AlphaAlp", "BetaBet", "GammaGam", "DeltaDel",
        "EpsilonEps", "ZetaZet", "EtaEta", "ThetaThet", "IotaIot",
        "KappaKap", "LambdaLam", "MuMu", "NuNu", "XiXi",
        "OmicronOmi", "PiPi", "RhoRho", "SigmaSig", "TauTau",
        "UpsilonUps", "PhiPhi", "ChiChi", "PsiPsi", "OmegaOmg",
        "AlphaWolf", "BetaFish", "GammaRay", "DeltaForce", "EpsilonTeam",
        "ZetaPotential", "EtaCarinae", "ThetaBrain", "IotaDevice", "KappaLogic",
        "LambdaCalc", "MuParticle", "NuClear", "XiPlanet", "OmicronStar",
        "PiValue", "RhoMethod", "SigmaRule", "TauTime", "UpsilonBond",
        "PhiAngle", "ChiSquare", "PsiWave", "OmegaEnd", "AlphaStart",
        "BetaTest", "GammaFunc", "DeltaVar", "EpsilonErr", "ZetaZero",
        "EtaForm", "ThetaState", "IotaIndex", "KappaKey", "LambdaExpr",
        "MuVal", "NuNum", "XiSet", "OmicronObj", "PiConst",
        "RhoRef", "SigmaSum", "TauType", "UpsilonUnit", "PhiFunc",
        "ChiClass", "PsiProc", "OmegaOp", "AlphaGo", "BetaBit",
        "GammaGen", "DeltaDat", "EpsilonEnc", "ZetaZip", "EtaExe",
        "ThetaTxt", "IotaImg", "KappaKey", "LambdaLib", "MuMod",
        "NuNet", "XiXml", "OmicronOpt", "PiPkg", "RhoRun",
        "SigmaSrc", "TauTmp", "UpsilonUsr", "PhiVar", "ChiCfg",
        "PsiPrj", "OmegaWrk", "AlphaApp", "BetaBin", "GammaCab",
        "DeltaDir", "EpsilonEnv", "ZetaExt", "EtaFmt", "ThetaGui",
        "IotaIni", "KappaJs", "LambdaLog", "MuMem", "NuNav",
        "XiObj", "OmicronOut", "PiPtr", "RhoReg", "SigmaScr",
        "TauTab", "UpsilonUi", "PhiVal", "ChiWin", "PsiXml",
        "OmegaZip", "AlphaOne", "BetaTwo", "GammaThree", "DeltaFour",
        "EpsilonFive", "ZetaSix", "EtaSeven", "ThetaEight", "IotaNine",
        "KappaTen", "LambdaEleven", "MuTwelve", "NuThirteen", "XiFourteen",
        "OmicronFifteen", "PiSixteen", "RhoSeventeen", "SigmaEighteen", "TauNineteen",
        "UpsilonTwenty", "PhiThirty", "ChiForty", "PsiFifty", "OmegaHundred"
    };
    
    static {
        initializeParts();
        loadSpecialNames();
    }
    
    
    private static void loadSpecialNames() {

        long currentTime = System.currentTimeMillis();
        if (specialNamesLoaded && (currentTime - lastLoadTime) < RELOAD_INTERVAL) {
            return;
        }
        

        try {
            System.out.println("[PVP Bot] Trying to load special names from URL: " + SPECIAL_NAMES_URL);
            String json = downloadJson(SPECIAL_NAMES_URL);
            if (json != null && parseSpecialNames(json)) {
                System.out.println("[PVP Bot] Successfully loaded special names from URL");
                specialNamesLoaded = true;
                lastLoadTime = currentTime;
                return;
            }
        } catch (Exception e) {
            System.out.println("[PVP Bot] Failed to load from URL: " + e.getMessage());
        }
        

        if (!specialNamesLoaded) {
            try {
                System.out.println("[PVP Bot] Loading special names from local resources");
                InputStream stream = BotNameGenerator.class.getResourceAsStream("/special_names.json");
                if (stream != null) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
                    StringBuilder json = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        json.append(line);
                    }
                    reader.close();
                    
                    if (parseSpecialNames(json.toString())) {
                        System.out.println("[PVP Bot] Successfully loaded special names from resources");
                        specialNamesLoaded = true;
                        lastLoadTime = currentTime;
                        return;
                    }
                }
            } catch (Exception e) {
                System.out.println("[PVP Bot] Failed to load from resources: " + e.getMessage());
            }
            

            System.out.println("[PVP Bot] Using default special names");
            specialNames.add("nantag");
            specialNames.add("Stepan1411");
            spawnChance = 10;
            specialNamesLoaded = true;
            lastLoadTime = currentTime;
        }
    }
    
    
    private static String downloadJson(String urlString) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("User-Agent", "PVP-Bot-Fabric");
            
            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                return response.toString();
            }
        } catch (Exception e) {

        }
        return null;
    }
    
    
    private static boolean parseSpecialNames(String json) {
        try {
            Gson gson = new Gson();
            JsonObject obj = gson.fromJson(json, JsonObject.class);
            
            if (obj.has("special_names") && obj.get("special_names").isJsonArray()) {
                specialNames.clear();
                obj.getAsJsonArray("special_names").forEach(element -> {
                    String name = element.getAsString();
                    if (name != null && !name.isEmpty() && name.length() <= 16) {
                        specialNames.add(name);
                    }
                });
            }
            
            if (obj.has("spawn_chance")) {
                spawnChance = obj.get("spawn_chance").getAsInt();
                if (spawnChance < 0) spawnChance = 0;
                if (spawnChance > 100) spawnChance = 100;
            }
            
            return !specialNames.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }
    
    
    public static List<String> getSpecialNames() {
        return new ArrayList<>(specialNames);
    }
    
    
    public static int getSpawnChance() {
        return spawnChance;
    }
    
    
    private static void initializeParts() {
        Set<String> prefixSet = new HashSet<>();
        Set<String> suffixSet = new HashSet<>();
        
        for (String name : BASE_NAMES) {
            List<String> parts = splitCamelCase(name);
            if (parts.size() >= 2) {
                prefixSet.add(parts.get(0));
                suffixSet.add(parts.get(parts.size() - 1));
            } else if (parts.size() == 1 && parts.get(0).length() > 4) {

                prefixSet.add(parts.get(0));
            }
        }
        
        PREFIXES.addAll(prefixSet);
        SUFFIXES.addAll(suffixSet);
    }
    
    
    private static List<String> splitCamelCase(String name) {
        List<String> parts = new ArrayList<>();
        Matcher matcher = CAMEL_CASE.matcher(name);
        while (matcher.find()) {
            parts.add(matcher.group(1));
        }
        return parts;
    }
    
    
    public static String generateUniqueName() {

        boolean useSpecialNames = org.stepan1411.pvp_bot.bot.BotSettings.get().isUseSpecialNames();
        
        loadSpecialNames();
        
        Set<String> existingBots = BotManager.getAllBots();
        

        if (useSpecialNames && !specialNames.isEmpty() && random.nextInt(100) < spawnChance) {
            String specialName = specialNames.get(random.nextInt(specialNames.size()));
            if (!existingBots.contains(specialName)) {
                return specialName;
            }
        }
        
        for (int attempt = 0; attempt < 100; attempt++) {
            String name = generateName();

            if (name.length() <= 16 && !existingBots.contains(name)) {
                return name;
            }
        }
        

        for (int num = 1; num < 1000; num++) {
            String name = "Bot" + num;
            if (!existingBots.contains(name)) {
                return name;
            }
        }
        return "Bot" + System.currentTimeMillis() % 10000;
    }
    
    
    private static String generateName() {
        for (int i = 0; i < 20; i++) {
            String prefix = PREFIXES.get(random.nextInt(PREFIXES.size()));
            String suffix = SUFFIXES.get(random.nextInt(SUFFIXES.size()));
            String name = prefix + suffix;
            if (name.length() <= 16) {
                return name;
            }
        }

        String prefix = PREFIXES.get(random.nextInt(PREFIXES.size()));
        if (prefix.length() > 12) prefix = prefix.substring(0, 12);
        return prefix + random.nextInt(1000);
    }
}
