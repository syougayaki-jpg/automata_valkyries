"""進捗（2026-10-30 ユーザー指定「進捗作りと本家らしく進捗名はパロディ多めで」）。

本家と同じく 3 つのタブ（戦術兵姫＝物語・クロノリンク＝工業・戦場＝ボス）に分け、名前はバニラの進捗や有名な作品のもじりにする。
同日「ネット・ミーム系統も揃えて（そんな装備で大丈夫か？やぬるぽガッなど）」でネットのお約束の言い回しも混ぜた。
この表から src/main/resources/data/automata_valkyries/advancements/<タブ>/<名前>.json と、言語ファイルの
advancements.automata_valkyries.<タブ>.<名前>.title / .description を作る（言語ファイルの既存の進捗の行は消してから足し直す）。

使い方: python tools/gen_advancements.py
"""
import json
import os
import re

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
NS = "automata_valkyries"
DATA = os.path.join(ROOT, "src/main/resources/data", NS)
LANG = os.path.join(ROOT, "src/main/resources/assets", NS, "lang")
RECIPES = os.path.join(DATA, "recipes/armory")


def tier_items(tier):
    """タクティカル・アーモリーのレシピでそのティアの兵装・道具・モジュール"""
    out = []
    for f in sorted(os.listdir(RECIPES)):
        r = json.load(open(os.path.join(RECIPES, f), encoding="utf-8"))
        item = r.get("result", {}).get("item", "")
        path = item.split(":")[-1]
        if r.get("tier") == tier and re.match(r"(weapon|tool|module)_", path):
            out.append(item)
    return out


def has(*items):
    """どれかを持っている"""
    return {"trigger": "minecraft:inventory_changed", "conditions": {"items": [{"items": [i if ":" in i else NS + ":" + i for i in items]}]}}


def has_all(*items):
    """全部を持っている（criteria を分ける）"""
    return {"_all": [has(i) for i in items]}


def kill(*entities):
    """どれかを倒した（# で始めるとタグ）"""
    crits = []
    for e in entities:
        pred = {"type": e if e.startswith("#") or ":" in e else NS + ":" + e}
        crits.append({"trigger": "minecraft:player_killed_entity",
                      "conditions": {"entity": [{"condition": "minecraft:entity_properties", "entity": "this", "predicate": pred}]}})
    return {"_any": crits} if len(crits) > 1 else crits[0]


TICK = {"trigger": "minecraft:tick"}
# コードから解除する（registry/ModAdvancements）
IMPOSSIBLE = {"trigger": "minecraft:impossible"}

# タブ, 名前, 親, アイコン, 条件, 枠（task / goal / challenge）, 隠す, 日本語の名前, 日本語の説明, 英語の名前, 英語の説明
A = [
    # ---- 戦術兵姫（物語） ----
    ("story", "root", None, "crashed_pod_hull", TICK, "task", False,
     "戦術兵姫", "未来から墜ちてきた兵器と少女の物語", "Tactical Valkyries", "A tale of weapons and girls that fell from the future"),
    ("story", "scrap", "root", "drop_chrono_scrap", has("drop_chrono_scrap"), "task", False,
     "スクラップ時代", "クロノスクラップを拾う", "Scrap Age", "Pick up some chrono scrap"),
    ("story", "astrite", "scrap", "ingot_astrite", has("ingot_astrite"), "task", False,
     "未来の金属を手に入れる", "アストライト・インゴットを手に入れる", "Acquire Future Hardware", "Get an astrite ingot"),
    ("story", "circuit_basic", "astrite", "circuit_basic", has("circuit_basic"), "task", False,
     "基本が大事", "基本回路を作る", "Back to Basics", "Make a basic circuit"),
    ("story", "circuit_advanced", "circuit_basic", "circuit_advanced", has("circuit_advanced"), "task", False,
     "アップグレード", "高等回路を作る", "Getting an Upgrade", "Make an advanced circuit"),
    ("story", "core_proto", "circuit_advanced", "core_proto", has("core_proto"), "goal", False,
     "次世代へ", "眠る兵姫を起こすプロトコアを作る", "The Next Generation", "Make a proto core to wake a sleeping valkyrie"),
    ("story", "valkyrie_body", "core_proto", "valkyrie_body_v", has("valkyrie_body_v", "valkyrie_body_s", "valkyrie_body_h", "valkyrie_body_c"), "task", False,
     "雇われ兵姫", "兵姫の素体を手に入れる", "Hired Valkyrie", "Get a valkyrie body"),
    ("story", "charging_dock", "valkyrie_body", "charging_dock", has("charging_dock"), "task", False,
     "実家のような安心感", "兵姫が帰ってくる給電ドックを作る", "Feels Like Home", "Make a charging dock for your valkyries to come home to"),
    ("story", "android_dream", "valkyrie_body", "core_proto", IMPOSSIBLE, "goal", False,
     "アンドロイドは電気羊の夢を見ない", "兵姫が電力切れで休眠する", "Androids Don't Dream of Electric Sheep", "Let one of your valkyries run out of power and fall dormant"),
    ("story", "tachyon_drive", "valkyrie_body", "backpack_tachyon_drive", IMPOSSIBLE, "goal", False,
     "当たらなければどうということはない", "兵姫にタキオンドライブを装備させる", "It Doesn't Matter If It Doesn't Hit", "Equip a valkyrie with a Tachyon Drive"),
    ("story", "slow_life", "valkyrie_body", "backpack_harvester",
     has("backpack_harvester", "backpack_mining_arm", "backpack_lumber_saw", "backpack_fishing_rig", "backpack_field_kitchen"), "task", False,
     "戦ったら負けだと思ってる", "戦わない兵姫のための背部ユニットを作る", "Fighting Means Losing", "Make a back unit for a valkyrie who does not fight"),
    ("story", "astrite_armor", "weapon_t1", "astrite_chestplate", has_all("astrite_helmet", "astrite_chestplate", "astrite_leggings", "astrite_boots"), "task", False,
     "大丈夫だ、問題ない", "アストライトの防具を全部そろえる", "There's No Problem", "Get a full set of astrite armor"),
    ("story", "pocket", "astrite", "item_pocket_t1", has("item_pocket_t1", "item_pocket_t2", "item_pocket_t3", "item_pocket_t4"), "task", False,
     "四次元ポケット", "アイテムポケットを作る", "Bigger on the Inside", "Make an item pocket"),
    ("story", "pocket_t4", "pocket", "item_pocket_t4", has("item_pocket_t4"), "challenge", False,
     "チーターや！", "ネバーラストのアイテムポケットを作る", "Cheater!", "Make a Neverlast item pocket"),
    ("story", "weapon_t1", "circuit_advanced", "weapon_photon_edge", has(*tier_items(1)), "task", False,
     "そんな装備で大丈夫か？", "T1 の兵装を手に入れる", "Are You Sure About That Gear?", "Get a T1 weapon"),
    ("story", "upgrade_station", "weapon_t1", "upgrade_station", has("upgrade_station"), "task", False,
     "ぼくのかんがえたさいきょうのへいそう", "兵装強化台を作る", "My Ultimate Weapon (Age 8)", "Make a weapon upgrade station"),
    ("story", "overload", "weapon_t1", "overload_memory", has("overload_memory"), "goal", False,
     "どうしてこうなった", "オーバーロードメモリを手に入れる", "How Did It Come to This", "Get an overload memory"),
    ("story", "weapon_t2", "weapon_t1", "weapon_heavy_particle_cannon", has(*tier_items(2)), "task", False,
     "さらなるティアへ", "T2 の兵装を手に入れる", "We Need to Go Higher", "Get a T2 weapon"),
    ("story", "weapon_t3", "weapon_t2", "weapon_quantum_blade", has(*tier_items(3)), "goal", False,
     "インフレの向こう側", "T3 の兵装を手に入れる", "Beyond Power Creep", "Get a T3 weapon"),
    ("story", "flight_wing", "weapon_t3", "tool_flight_wing", has("tool_flight_wing"), "goal", False,
     "空も飛べるはず", "光翼の飛翔機を手に入れる", "I Believe I Can Fly", "Get a Light Wing Flyer"),
    ("story", "weapon_t4", "weapon_t3", "weapon_luminous_requiem", has(*tier_items(4)), "challenge", False,
     "一番いいのを頼む", "T4 の兵装を手に入れる", "Give Me the Best One", "Get a T4 weapon"),

    # ---- クロノリンク（工業） ----
    ("chrono", "root", None, "luminal_reactor", TICK, "task", False,
     "クロノリンク", "工業と時間の扉を開く", "Chronolink", "Open the door to industry and time"),
    ("chrono", "generator", "root", "generator", has("generator"), "task", False,
     "ホットな発電", "発電機を作る", "Hot Topic", "Make a generator"),
    ("chrono", "assembler", "generator", "assembler", has("assembler"), "task", False,
     "組み立てが必要です", "組み立て機を作る", "Assembly Required", "Make an assembler"),
    ("chrono", "blueprint", "assembler", "blueprint_card", has("blueprint_card"), "task", False,
     "ぬるぽ", "中身が空っぽ（null）の設計図カードを作る", "NullPointerException", "Make a blueprint card with nothing (null) on it"),
    ("chrono", "fabricator", "blueprint", "chrono_fabricator", has("chrono_fabricator"), "task", False,
     "ガッ", "クロノ・ファブリケータを作って、空っぽの設計図にレシピを叩き込む", "Gah!", "Make a Chrono Fabricator and slam a recipe into that empty blueprint"),
    ("chrono", "reactor", "generator", "luminal_reactor", has("luminal_reactor"), "task", False,
     "光あれ", "ルミナル反応炉を作る", "Let There Be Light", "Make a Luminal Reactor"),
    ("chrono", "adapter", "reactor", "luminal_adapter", has("luminal_adapter"), "task", False,
     "等価交換", "ルミナルエナジーアダプターを作る", "Equivalent Exchange", "Make a Luminal Energy Adapter"),
    ("chrono", "quantum_tac", "adapter", "circuit_quantum_tac", has("circuit_quantum_tac"), "task", False,
     "量子のいたずら", "量子TAC集積回路を作る", "Quantum Shenanigans", "Make a quantum TAC circuit"),
    ("chrono", "photonic", "quantum_tac", "circuit_photonic", has("circuit_photonic"), "task", False,
     "光の速さで", "フォトニック回路を作る", "Photonic Boom", "Make a photonic circuit"),
    ("chrono", "chrono_lattice", "photonic", "circuit_chrono_lattice", has("circuit_chrono_lattice"), "goal", False,
     "時空を超えて", "時空格子回路を作る", "Through Time and Space", "Make a chrono lattice circuit"),
    ("chrono", "causal", "chrono_lattice", "circuit_causal", has("circuit_causal"), "challenge", False,
     "因果応報", "因果演算回路を作る", "Cause and Effect", "Make a causal circuit"),
    ("chrono", "frame_future", "adapter", "frame_future", has("frame_future"), "task", False,
     "バック・トゥ・ザ・フューチャー", "フューチャー・フレームを作る", "Back to the Future", "Make a Future frame"),
    ("chrono", "frame_beyond", "frame_future", "frame_beyond", has("frame_beyond"), "goal", False,
     "無限の彼方へ", "ビヨンド・フレームを作る", "To Infinity and Beyond", "Make a Beyond frame"),
    ("chrono", "frame_neverlast", "frame_beyond", "frame_neverlast", has("frame_neverlast"), "challenge", False,
     "ネバーエンディング・ストーリー", "ネバーラスト・フレームを作る", "The NeverEnding Story", "Make a Neverlast frame"),
    ("chrono", "star", "reactor", "star_luminal", has("star_luminal"), "goal", False,
     "スター誕生", "恒星崩壊のルミナルスターを手に入れる", "A Star Is Born", "Get a collapsed luminal star"),
    ("chrono", "genesis", "star", "core_genesis_luminal", has("core_genesis_luminal"), "challenge", False,
     "永久機関、完成", "創世ルミナルコアを手に入れる", "Perpetual Motion, Achieved", "Get a Genesis Luminal Core"),
    ("chrono", "repository", "reactor", "void_repository", has("void_repository"), "task", False,
     "どこでも収納", "ヴォイド・リポジトリを作る", "Bottomless Box", "Make a Void Repository"),
    ("chrono", "accelerator", "reactor", "chrono_accelerator", has("chrono_accelerator"), "task", False,
     "ありのまま今起こったことを話すぜ", "時間加速器を作る", "I'll Tell You What Just Happened", "Make a Time Accelerator"),
    ("chrono", "rewinder", "accelerator", "chrono_rewinder", has("chrono_rewinder"), "goal", False,
     "時を戻そう", "時間巻き戻し機を作る", "Let's Turn Back Time", "Make a Time Rewinder"),

    # ---- 戦場（ボス） ----
    ("battle", "root", None, "weapon_photon_edge", TICK, "task", False,
     "戦場", "侵食された世界で戦う", "Battlefield", "Fight in a corrupted world"),
    ("battle", "chrono_hunter", "root", "drop_distorted_chrono_shard", kill("#" + NS + ":chrono_enemies"), "task", False,
     "クロノハンター", "クロノゲイザーの機械兵を倒す", "Chrono Hunter", "Defeat a Chrono-Gazer machine"),
    ("battle", "outpost_watcher", "chrono_hunter", "chrono_key", kill("outpost_watcher"), "task", False,
     "前哨戦", "前哨司令機 ウォッチャーを倒す", "Skirmish", "Defeat the Outpost Watcher"),
    ("battle", "foundry_master", "outpost_watcher", "alloy_chrono_fortress", kill("foundry_master"), "goal", False,
     "工場見学", "工場長機 ファウンドリ・マスターを倒す", "Factory Tour", "Defeat the Foundry Master"),
    ("battle", "bastion_keeper", "foundry_master", "star_chronos", kill("bastion_keeper"), "goal", False,
     "勝ったな、風呂入ってくる", "要塞守護機 バスティオン・キーパーを倒す", "We Won, I'm Taking a Bath", "Defeat the Bastion Keeper"),
    ("battle", "time_ruler", "bastion_keeper", "boss_drop_ruler_heart", kill("time_ruler"), "challenge", False,
     "やったか！？", "総司令機 タイムルーラーを倒す", "Did We Get Him?!", "Defeat the Time Ruler"),
    ("battle", "lost_catastrophe", "time_ruler", "boss_drop_purified_zero", kill("lost_catastrophe"), "challenge", True,
     "おしまい。", "戦術兵姫零号機を倒す", "The End.", "Defeat Unit Zero"),
    ("battle", "solo_zero", "lost_catastrophe", "weapon_catastrophe_buster", IMPOSSIBLE, "challenge", True,
     "誰が為の闘争", "戦術兵姫を追従させず、一人で戦術兵姫零号機を倒す", "For Whom Do We Fight", "Defeat Unit Zero alone, without any valkyrie following you"),
    ("battle", "luxion", "chrono_hunter", "boss_drop_luxion_core", kill("luxion"), "goal", False,
     "光より速く", "最光速『ルクシオン』を倒す", "Faster Than Light", "Defeat Luxion"),
    ("battle", "luxion_ex", "luxion", "boss_drop_ftl_accel", kill("luxion_ex"), "challenge", False,
     "光速の向こう側", "ルクシオン EX を倒す", "Ludicrous Speed", "Defeat Luxion EX"),
    ("battle", "galliafort", "luxion", "boss_drop_gallia_plate", kill("galliafort"), "goal", False,
     "鉄壁崩し", "重護屋『ガリアフォート』を倒す", "Breaking the Wall", "Defeat Gallia Fort"),
    ("battle", "galliafort_ex", "galliafort", "boss_drop_distortion_gen", kill("galliafort_ex"), "challenge", False,
     "難攻不落、陥落", "ガリアフォート EX を倒す", "Impregnable No More", "Defeat Gallia Fort EX"),
    ("battle", "memento_mori", "galliafort", "boss_drop_memento_wings", kill("memento_mori"), "goal", False,
     "蝶のように舞い", "黒死蝶『メメントモリ』を倒す", "Float Like a Butterfly", "Defeat Memento Mori"),
    ("battle", "memento_mori_ex", "memento_mori", "boss_drop_necro_colony", kill("memento_mori_ex"), "challenge", False,
     "死を想え", "メメントモリ EX を倒す", "Remember You Must Die", "Defeat Memento Mori EX"),
    ("battle", "trial", "luxion", "trial_hourglass", has("trial_hourglass"), "task", False,
     "強くてニューゲーム", "試練の砂時計を手に入れる", "New Game Plus", "Get a trial hourglass"),
    ("battle", "akashic_seer", "chrono_hunter", "boss_drop_akashic_core", kill("akashic_seer"), "goal", False,
     "見る者を見る", "事象観測姫アカシックを倒す", "Who Watches the Watcher", "Defeat Akashic the Seer"),
    ("battle", "akashic_cubes", "akashic_seer", "akashic_cubes", has("akashic_cubes"), "task", False,
     "観測者効果", "観測の立方体を作る", "The Observer Effect", "Make the Observation Cubes"),
    ("battle", "elenor", "memento_mori", "boss_drop_elenor_ribbon", kill("elenor"), "challenge", False,
     "期待以上ですね……♪", "エレノアを倒す", "Better Than Expected…♪", "Defeat Elenor"),
    ("battle", "farthest_elenor", "elenor", "signal_elenor", kill("farthest_elenor"), "challenge", True,
     "最果てのその先へ", "最果てのエレノアを倒す", "Beyond the Farthest", "Defeat the Farthest Elenor"),
    ("battle", "tiny_courage", "elenor", "tiny_courage", has("tiny_courage"), "challenge", True,
     "小さな勇気、大きな一歩", "ちっぽけな勇気を手に入れる", "One Small Courage", "Get a tiny courage"),
]

CHRONO_ENEMIES = ["chrono_scout_drone", "chrono_spider_bot", "chrono_heavy_walker", "chrono_mask_drone", "chrono_trooper", "pulse_phalanx",
                  "siege_cannon", "siren_eye", "chrono_guardian", "mimic_container", "crane_loader", "shield_drone", "phase_condenser",
                  "chrono_interceptor", "delay_node"]
BACKGROUNDS = {"story": "minecraft:textures/block/polished_deepslate.png", "chrono": NS + ":textures/block/tech_panel_dark.png",
               "battle": "minecraft:textures/block/crying_obsidian.png"}
XP = {"task": 0, "goal": 0, "challenge": 100}


def main():
    adv_root = os.path.join(DATA, "advancements")
    for tab, name, parent, icon, cond, frame, hidden, *_ in A:
        key = "advancements.%s.%s.%s" % (NS, tab, name)
        display = {"icon": {"item": icon if ":" in icon else NS + ":" + icon},
                   "title": {"translate": key + ".title"}, "description": {"translate": key + ".description"},
                   "frame": frame, "show_toast": parent is not None, "announce_to_chat": parent is not None, "hidden": hidden}
        if parent is None:
            display["background"] = BACKGROUNDS[tab]
        if "_all" in cond:
            criteria = {"has_%d" % i: c for i, c in enumerate(cond["_all"])}
            requirements = [[k] for k in criteria]
        elif "_any" in cond:
            criteria = {"c%d" % i: c for i, c in enumerate(cond["_any"])}
            requirements = [list(criteria)]
        else:
            criteria = {"c": cond}
            requirements = [["c"]]
        adv = {"display": display, "criteria": criteria, "requirements": requirements}
        if parent is not None:
            adv = {"parent": "%s:%s/%s" % (NS, tab, parent), **adv}
        if XP[frame]:
            adv["rewards"] = {"experience": XP[frame]}
        out = os.path.join(adv_root, tab, name + ".json")
        os.makedirs(os.path.dirname(out), exist_ok=True)
        with open(out, "w", encoding="utf-8", newline="\n") as f:
            json.dump(adv, f, ensure_ascii=False, indent=2)
            f.write("\n")
    tag = os.path.join(DATA, "tags/entity_types/chrono_enemies.json")
    os.makedirs(os.path.dirname(tag), exist_ok=True)
    with open(tag, "w", encoding="utf-8", newline="\n") as f:
        json.dump({"replace": False, "values": [NS + ":" + e for e in CHRONO_ENEMIES]}, f, indent=2)
        f.write("\n")
    for lang, ti, di in (("ja_jp.json", 7, 8), ("en_us.json", 9, 10)):
        p = os.path.join(LANG, lang)
        lines = open(p, encoding="utf-8").read().split("\n")
        lines = [l for l in lines if not l.strip().startswith('"advancements.%s.' % NS)]
        close = max(i for i, l in enumerate(lines) if l.strip() == "}")
        last = max(i for i in range(close) if lines[i].strip())
        if not lines[last].rstrip().endswith(","):
            lines[last] = lines[last].rstrip() + ","
        add = []
        for row in A:
            tab, name = row[0], row[1]
            key = "advancements.%s.%s.%s" % (NS, tab, name)
            add.append('  %s: %s,' % (json.dumps(key + ".title"), json.dumps(row[ti], ensure_ascii=False)))
            add.append('  %s: %s,' % (json.dumps(key + ".description"), json.dumps(row[di], ensure_ascii=False)))
        add[-1] = add[-1].rstrip(",")
        lines[last + 1:last + 1] = add
        text = "\n".join(lines)
        json.loads(text)
        open(p, "w", encoding="utf-8", newline="").write(text)
    print(len(A), "advancements")


if __name__ == "__main__":
    main()
