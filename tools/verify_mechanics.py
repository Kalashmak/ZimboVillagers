"""Verify explicit in-game assertions; clean Gradle shutdown alone is never a mechanics pass."""
import argparse,json,os,re
from pathlib import Path
# The client's game directory: run-client, or run-client-N for a parallel probe (tools/astra.py probes sets ASTRA_GAME_DIR).
GAME_DIR=Path(os.environ.get('ASTRA_GAME_DIR') or Path(__file__).resolve().parents[1]/'run-client')
parser=argparse.ArgumentParser()
parser.add_argument('log',type=Path)
parser.add_argument('--mode',choices=['live-production-smelt','live-production','live-upgrade','tower-stages','building-signs','ballista','medicine-reload','medicine-pickup','medicine-wolf','smithy-courier','smithy-wolf','patrol','engineering-trap','lab-desks','raid-unload','medicine','construction-ladder','school','dialog','warehouse','autoyard','grazing','wolves','research-tree-v2','research-facts','architecture-migration','creative-trade','resident-markers','growth-plots','natural-supply','office-ui','cargo','farm','cargo-crash','construction','office','election','draft','crops','science','logistics','research-graph','world-interaction','plan','war','load','map','maptools','raid','battle','night','level','quarry','mayor-tool','building-order','autonomy','trade','atlas','road','caravan','quest','camp','adventure','wild','chain','escort','wall','drill','cart-recovery','starter-food','remote-village','core','gift','schematic','wall-ring','relocate','farm-levels','forester','annex','livestock','housing-beds','restaurant','framed-window','furniture','animal','escort-reload','quest-talk','wolf-rescue'],required=True)
parser.add_argument('--reload',action='store_true')
parser.add_argument('--caravan-escort',action='store_true')
parser.add_argument('--caravan-dog',action='store_true')
parser.add_argument('--caravan-horse',action='store_true')
parser.add_argument('--caravan-checkpoint',action='store_true')
parser.add_argument('--boundary',choices=['after_intent','after_chunk_flush'])
args=parser.parse_args();log=args.log.read_text(encoding='utf-8',errors='replace')
assert not any(marker in log for marker in ['ASTRA_CARGO FAILED','ASTRA_FARM FAILED','ASTRA_CONSTRUCTION FAILED','ASTRA_SMOKE FAILED','ASTRA_OFFICE FAILED','ASTRA_ELECTION FAILED','ASTRA_DRAFT FAILED','ASTRA_CROPS FAILED','ASTRA_SCIENCE FAILED','ASTRA_LOGISTICS FAILED','ASTRA_RESEARCH_GRAPH FAILED','ASTRA_WORLD_INTERACTION FAILED','ASTRA_MAYOR FAILED']),'Mechanics harness failed'
assert 'ASTRA_BUILD_ORDER FAILED' not in log,'Building order harness failed'
if args.mode in ('live-production','live-production-smelt'):
    assert 'ASTRA_LIVE_PRODUCTION FAILED' not in log and 'BUILD SUCCESSFUL' in log
    assert log.count('ASTRA_LIVE_PRODUCTION VERIFIED reload='+str(args.reload).lower()+' paid=true physicalWorker=true stockIdentity=true')==1
    if args.reload: assert 'ASTRA_LIVE_PRODUCTION completed upgraded=3 outputs=2 noReplay=true' in log
    if args.mode=='live-production-smelt': assert ('ASTRA_LIVE_PRODUCTION ironChain raw=6 picks=2 vanillaFurnace=true' if args.reload else 'ASTRA_LIVE_PRODUCTION furnace physicalRaw=true paidCoal=true lit=true') in log
    shots=re.findall(r'ASTRA_LIVE_PRODUCTION screenshot (\S+\.png)',log)
    assert len(shots)==1
    raw=(GAME_DIR/shots[0]).resolve().read_bytes()
    assert raw[:8]==bytes.fromhex('89504e470d0a1a0a') and len(raw)>10000
    print(json.dumps({'status':'PASSED','mode':args.mode,'reload':args.reload,'frames':shots}));raise SystemExit(0)
if args.mode=='live-upgrade' and args.reload:
    assert 'ASTRA_LIVE_UPGRADE_RELOAD FAILED' not in log and 'BUILD SUCCESSFUL' in log
    assert log.count('ASTRA_LIVE_UPGRADE_RELOAD VERIFIED restart=true activeProject=true cured=true paidBandage=1 continued=true walked=true')==1
    print(json.dumps({'status':'PASSED','mode':args.mode,'reload':True}));raise SystemExit(0)
if args.mode=='live-upgrade':
    assert 'ASTRA_LIVE_UPGRADE FAILED' not in log and 'BUILD SUCCESSFUL' in log
    assert log.count('ASTRA_LIVE_UPGRADE VERIFIED naturalTicks=true activeProject=true cured=true paidBandage=1 stockIdentity=true oldLevel=2 walked=true')==1
    shots=re.findall(r'ASTRA_LIVE_UPGRADE screenshot (\S+\.png)',log)
    assert len(shots)==1
    raw=(GAME_DIR/shots[0]).resolve().read_bytes()
    assert raw[:8]==bytes.fromhex('89504e470d0a1a0a') and len(raw)>10000
    print(json.dumps({'status':'PASSED','mode':args.mode,'frames':shots}));raise SystemExit(0)
if args.mode=='tower-stages':
    assert 'ASTRA_TOWER_STAGES FAILED' not in log and 'BUILD SUCCESSFUL' in log
    assert log.count('ASTRA_TOWER_STAGES VERIFIED naturalTicks=true paid=true walked=true tiers=2,4,5 chest=true noFreeUpgrade=true')==1
    assert 'ASTRA_TOWER_STAGES completed tier=4' in log and 'ASTRA_TOWER_STAGES completed tier=5' in log
    shots=re.findall(r'ASTRA_TOWER_STAGES screenshot (\S+\.png)',log)
    assert len(shots)==1
    raw=(GAME_DIR/shots[0]).resolve().read_bytes()
    assert raw[:8]==bytes.fromhex('89504e470d0a1a0a') and len(raw)>10000
    print(json.dumps({'status':'PASSED','mode':args.mode,'frames':shots}));raise SystemExit(0)
if args.mode=='building-signs':
    assert 'ASTRA_BUILDING_SIGNS FAILED' not in log and 'BUILD SUCCESSFUL' in log
    assert log.count('ASTRA_BUILDING_SIGNS VERIFIED blockUse=true selected=true menu=true')==1
    shots=re.findall(r'ASTRA_BUILDING_SIGNS screenshot (\S+\.png)',log)
    assert len(shots)==1
    raw=(GAME_DIR/shots[0]).resolve().read_bytes()
    assert raw[:8]==bytes.fromhex('89504e470d0a1a0a') and len(raw)>10000
    print(json.dumps({'status':'PASSED','mode':args.mode,'frames':shots}));raise SystemExit(0)
if args.mode=='ballista':
    assert 'ASTRA_BALLISTA FAILED' not in log and 'BUILD SUCCESSFUL' in log
    assert log.count('ASTRA_BALLISTA VERIFIED naturalTicks=true vQuiet=true viBolt=true pierce=3 damage=true range=40')==1
    shots=re.findall(r'ASTRA_BALLISTA screenshot (\S+\.png)',log)
    assert len(shots)==1
    raw=(GAME_DIR/shots[0]).resolve().read_bytes()
    assert raw[:8]==bytes.fromhex('89504e470d0a1a0a') and len(raw)>10000
    print(json.dumps({'status':'PASSED','mode':args.mode,'frames':shots}));raise SystemExit(0)
if args.mode=='research-tree-v2':
    # AD-136: the tree v2 probe - 22 branches, side rows, engineering VI empty, a level-I node paid in resources from the hall chest through
    # the GUI, works 0/7 on a level-II card, a PLANNED card only in the owner's words, one work per 36000 clock ticks, the hall-cap refusal.
    assert 'ASTRA_TREE_V2 FAILED' not in log and 'BUILD SUCCESSFUL' in log
    lines=[l for l in log.splitlines() if 'ASTRA_TREE_V2 VERIFIED' in l]
    assert len(lines)==1,'Missing tree v2 verification'
    for part in ('branches=22','side_rows=true','empty_vi=true','pay_card=true','tier2_works=true','planned=true','paid=engineering.1','logs=0','table=0','work=1','refusal=hall','reload=false'):
        assert part in lines[0],'Tree v2: missing '+part
    shots=re.findall(r'ASTRA_TREE_V2 screenshot (\S+\.png)',log)
    assert len(shots)==3 and len(set(shots))==3
    for path in shots:
        raw=(GAME_DIR/path).resolve().read_bytes()
        assert raw[:8]==bytes.fromhex('89504e470d0a1a0a') and len(raw)>10000
    print(json.dumps({'status':'PASSED','mode':args.mode,'frames':shots}));raise SystemExit(0)
if args.mode=='research-facts':
    assert 'ASTRA_RESEARCH_FACTS FAILED' not in log and 'BUILD SUCCESSFUL' in log
    assert log.count('ASTRA_RESEARCH_FACTS VERIFIED cards=5 selection=true translations=true layout=true')==1
    shots=re.findall(r'ASTRA_RESEARCH_FACTS screenshot (\S+\.png)',log)
    assert len(shots)==5 and len(set(shots))==5
    for path in shots:
        raw=(GAME_DIR/path).resolve().read_bytes()
        assert raw[:8]==bytes.fromhex('89504e470d0a1a0a') and len(raw)>10000
    print(json.dumps({'status':'PASSED','mode':args.mode,'frames':shots}));raise SystemExit(0)
if args.mode=='architecture-migration':
    assert 'ASTRA_MIGRATION_PROBE FAILED' not in log and 'BUILD SUCCESSFUL' in log
    assert log.count('ASTRA_MIGRATION_PROBE VERIFIED level=4 inventory=true journal=true reload='+str(args.reload).lower())==1
    shots=re.findall(r'ASTRA_MIGRATION_PROBE screenshot (\S+\.png)',log)
    assert len(shots)==(1 if args.reload else 2)
    for path in shots:
        raw=(GAME_DIR/path).resolve().read_bytes()
        assert raw[:8]==bytes.fromhex('89504e470d0a1a0a') and len(raw)>10000
    print(json.dumps({'status':'PASSED','mode':args.mode,'reload':args.reload,'frames':shots}));raise SystemExit(0)
if args.mode=='gift':
    assert 'ASTRA_GIFT FAILED' not in log and 'BUILD SUCCESSFUL' in log
    assert len(re.findall(r'ASTRA_GIFT VERIFIED card=1 worn=1 total=1312 tall=true short=true',log))==1
    shots=re.findall(r'ASTRA_GIFT screenshot (\S+\.png)',log)
    assert len(shots)==3 and any(s.endswith('-gift-card.png') for s in shots) and any(s.endswith('-gift-worn.png') for s in shots)
    for path in shots:
        raw=(GAME_DIR/path).resolve().read_bytes()
        assert raw[:8]==bytes.fromhex('89504e470d0a1a0a') and len(raw)>10000
    print(json.dumps({'status':'PASSED','mode':args.mode,'frames':shots}));raise SystemExit(0)
if args.mode=='creative-trade':
    assert 'ASTRA_CREATIVE_TRADE FAILED' not in log and 'BUILD SUCCESSFUL' in log
    assert log.count('ASTRA_CREATIVE_TRADE VERIFIED emptyInventory=true reputation=true limit=2304;')==1
    shots=re.findall(r'ASTRA_CREATIVE_TRADE screenshot (\S+\.png)',log)
    assert len(shots)==2
    for path in shots:
        raw=(GAME_DIR/path).resolve().read_bytes()
        assert raw[:8]==bytes.fromhex('89504e470d0a1a0a') and len(raw)>10000
    print(json.dumps({'status':'PASSED','mode':args.mode,'frames':shots}));raise SystemExit(0)
if args.mode=='resident-markers':
    assert 'ASTRA_MARKERS FAILED' not in log and 'BUILD SUCCESSFUL' in log
    assert log.count('ASTRA_MARKERS VERIFIED profession=true realNeed=true quest=true cleared=true rangeRefresh=true reassignment=true')==1
    shots=re.findall(r'ASTRA_MARKERS screenshot (\S+\.png)',log)
    assert len(shots)==3
    for path in shots:
        raw=(GAME_DIR/path).resolve().read_bytes()
        assert raw[:8]==bytes.fromhex('89504e470d0a1a0a') and len(raw)>10000
    print(json.dumps({'status':'PASSED','mode':args.mode,'frames':shots}));raise SystemExit(0)
if args.mode=='growth-plots':
    assert 'ASTRA_GROWTH FAILED' not in log and 'BUILD SUCCESSFUL' in log
    assert log.count('ASTRA_GROWTH VERIFIED expansion=6 passage=6 physicalApproval=true')==1
    shots=re.findall(r'ASTRA_GROWTH screenshot (\S+\.png)',log)
    assert len(shots)==1
    raw=(GAME_DIR/shots[0]).resolve().read_bytes()
    assert raw[:8]==bytes.fromhex('89504e470d0a1a0a') and len(raw)>10000
    print(json.dumps({'status':'PASSED','mode':args.mode,'frames':shots}));raise SystemExit(0)
if args.mode=='natural-supply':
    assert 'ASTRA_SUPPLY FAILED' not in log and 'BUILD SUCCESSFUL' in log
    assert 'ASTRA_SUPPLY VERIFIED removed=2 glass=2 physicalInput=true vanillaFire=true paidFuel=true reload=false' in log
    shots=re.findall(r'ASTRA_SUPPLY screenshot (\S+\.png)',log)
    assert len(shots)==1
    raw=(GAME_DIR/shots[0]).resolve().read_bytes()
    assert raw[:8]==bytes.fromhex('89504e470d0a1a0a') and len(raw)>10000
    print(json.dumps({'status':'PASSED','mode':args.mode,'frames':shots}));raise SystemExit(0)
if args.mode=='farm-levels':
    # AD-130: the farm raised I..VI by its own projects; plots 80/320/480/960/1440/1440, barn plots lit >= 9, farmers = slots, the grid's clicks saved.
    assert 'ASTRA_FARM_LEVELS FAILED' not in log,'Farm levels harness failed: '+(re.findall(r'ASTRA_FARM_LEVELS FAILED.*',log) or [''])[0]
    lines=[line for line in log.splitlines() if 'FARM LEVELS PROBE VERIFIED' in line]
    assert len(lines)==1 and 'BUILD SUCCESSFUL' in log,'Missing verified farm levels'
    facts=dict(re.findall(r'(\w+)=(\S+)',lines[0].split('VERIFIED',1)[1]))
    assert facts.get('plots')=='80,320,480,960,1440,1440','Plots by level: %s'%facts.get('plots')
    assert int(facts.get('light','0'))>=9,'Lowest plot light: %s'%facts.get('light')
    assert facts.get('farmers')=='1/1,1/1,1/1,2/2,3/3,0/0','Farmers by level: %s'%facts.get('farmers')
    assert facts.get('crop')=='potato' and facts.get('f7')=='carrot' and facts.get('f1')=='potato' and facts.get('file')=='true','Policy after the clicks: %s'%facts
    grids=re.findall(r'ASTRA_FARM_LEVELS grid s(\d) \d+x\d+ problems=\[(.*?)\]',log)
    assert sorted(g for g,_ in grids)==['2','3'] and not [p for _,p in grids if p.strip()],'Grid layout: %s'%grids
    shots=re.findall(r'ASTRA_FARM_LEVELS screenshot (\S+\.png)',log)
    assert len(shots)==13 and len(set(shots))==13,'Expected 13 frames: 6 levels, 5 inside, 2 grids: %d'%len(shots)
    for shot in shots:
        raw=(Path(__file__).resolve().parents[1]/'run-client'/shot).resolve().read_bytes()
        assert raw[:8]==bytes.fromhex('89504e470d0a1a0a') and len(raw)>10000,'Frame is not a real PNG: '+shot
    print(json.dumps({'status':'PASSED','mode':args.mode,'facts':facts,'frames':shots},indent=2));raise SystemExit(0)
if args.mode=='forester':
    # AD-131: the hut raised I..VI; six markers from the live game, the grove against the forester over the same five minutes.
    assert 'ASTRA_FORESTER FAILED' not in log,'Forester harness failed: '+(re.findall(r'ASTRA_FORESTER FAILED.*',log) or [''])[0]
    facts={}
    for marker in ('FORESTER_WILD','FORESTER_REPLANT','FORESTER_VARIETY','SAWMILL_PLANKS','FORESTER_SPEED','GROVE_RATE'):
        lines=[line for line in log.splitlines() if marker+' VERIFIED' in line]
        assert len(lines)==1,'Missing or repeated '+marker
        facts[marker]=dict(re.findall(r'(\w+)=(\S+)',lines[0].split('VERIFIED',1)[1]))
    assert len([line for line in log.splitlines() if 'FORESTER PROBE VERIFIED' in line])==1 and 'BUILD SUCCESSFUL' in log,'Missing verified forester probe'
    d=float(facts['FORESTER_WILD']['distance']);assert 15<=d<=25 and facts['FORESTER_WILD']['player']=='standing' and facts['FORESTER_WILD']['planks']=='standing',facts['FORESTER_WILD']
    assert int(facts['FORESTER_VARIETY']['kinds'])>=3 and int(facts['FORESTER_VARIETY']['asks'])>=1,facts['FORESTER_VARIETY']
    assert int(facts['SAWMILL_PLANKS']['cuts'])>=3 and facts['SAWMILL_PLANKS']['planks_per_log']=='6' and facts['SAWMILL_PLANKS']['bad']=='0',facts['SAWMILL_PLANKS']
    assert int(facts['FORESTER_SPEED']['v'])>int(facts['FORESTER_SPEED']['iv']),facts['FORESTER_SPEED']
    g=facts['GROVE_RATE'];assert int(g['window_ticks'])==6000 and int(g['grove'])>=1.5*int(g['forester']),g
    shots=re.findall(r'ASTRA_FORESTER screenshot (\S+\.png)',log)
    assert len(shots)>=7 and len(set(shots))==len(shots),'Expected the level frames, the card and the courtyard: %d'%len(shots)
    for shot in shots:
        raw=(Path(__file__).resolve().parents[1]/'run-client'/shot).resolve().read_bytes()
        assert raw[:8]==bytes.fromhex('89504e470d0a1a0a') and len(raw)>10000,'Frame is not a real PNG: '+shot
    print(json.dumps({'status':'PASSED','mode':args.mode,'facts':facts,'frames':shots},indent=2));raise SystemExit(0)
if args.mode=='core':
    # AD-112 §14: the farm core crafted from its recipe, ordered in the Levels tab and set by the builder; the tab read at 427x240 and 320x240.
    assert 'ASTRA_CORE FAILED' not in log,'Core harness failed: '+(re.findall(r'ASTRA_CORE FAILED.*',log) or [''])[0]
    lines=[line for line in log.splitlines() if 'ASTRA_CORE VERIFIED' in line]
    assert len(lines)==1 and 'BUILD SUCCESSFUL' in log,'Missing verified farm core'
    for fact in ('grade=2','level=2','worked=4','plots=320','chestCore=0','ring3=','layout=true'):assert fact in lines[0],'Core probe did not verify '+fact
    assert 'ASTRA_CORE crafted villageastra:core_farm' in log,'The core was not assembled from its recipe'
    layouts=re.findall(r'ASTRA_CORE layout (before|after) s(\d) \d+x240 problems=\[(.*?)\]',log)
    assert sorted((w,sc) for w,sc,_ in layouts)==[('after','2'),('after','3'),('before','2'),('before','3')],'Expected the Levels tab before and after at both scales: %s'%layouts
    assert not [x for x in layouts if x[2].strip()],'Layout problems: %s'%layouts
    shots=re.findall(r'ASTRA_CORE screenshot (\S+\.png)',log)
    assert len(shots)==6 and len(set(shots))==6,'Expected six distinct core frames'
    for shot in shots:
        raw=(GAME_DIR/shot).resolve().read_bytes()
        assert raw[:8]==bytes.fromhex('89504e470d0a1a0a') and len(raw)>10000,'Core frame is not a real PNG: '+shot
    print(json.dumps({'status':'PASSED','mode':args.mode,'frames':shots},indent=2));raise SystemExit(0)
if args.mode=='schematic':
    # AD-126: estimate, half-built house, cut-away and focus frames; one step rebakes <=2 layers, idle snapshots none, VBO path (no fallback).
    assert 'ASTRA_SCHEMATIC FAILED' not in log,'Schematic harness failed: '+(re.findall(r'ASTRA_SCHEMATIC FAILED.*',log) or [''])[0]
    lines=[line for line in log.splitlines() if 'ASTRA_SCHEMATIC VERIFIED' in line]
    assert len(lines)==1 and 'BUILD SUCCESSFUL' in log,'Missing verified schematic'
    assert 'fallback=false' in lines[0] and 'idleBakes=0' in lines[0],'Schematic fell back or baked while idle'
    facts=dict(re.findall(r'(\w+)=(\S+)',lines[0].split('VERIFIED',1)[1]))
    assert int(facts['layers'])>0 and int(facts['vertices'])>0 and int(facts['rebakeOnStep'])<=2 and int(facts['fades'])>=1 and int(facts['cut'])>0 and float(facts['drawMsAvg'])<=4,facts
    shots=re.findall(r'ASTRA_SCHEMATIC screenshot (\S+\.png)',log)
    assert sorted(s.rsplit('-schematic-',1)[1] for s in shots)==['cutaway.png','draft.png','focus.png','half.png'],'Expected four schematic frames: %s'%shots
    for shot in shots:
        frame=(GAME_DIR/shot).resolve()
        assert frame.is_relative_to(Path(__file__).resolve().parents[1]/'docs/runs') and frame.read_bytes()[:8]==bytes.fromhex('89504e470d0a1a0a') and frame.stat().st_size>10000,'Not a real frame: '+shot
    print(json.dumps({'status':'PASSED','mode':args.mode,'facts':facts,'frames':shots},indent=2));raise SystemExit(0)
if args.mode=='office-ui':
    # AD-124: the office as the mayor sees it, its eight tabs held to the layout rules and captured at five GUI sizes
    # (427x240, 320x240, 480x270, 640x360, 1280x720 with the capped centred frame), and the research tree driven through
    # seven scenarios at 427x240 and 1280x720.
    assert 'ASTRA_OFFICE_UI FAILED' not in log,'Office UI harness failed: '+(re.findall(r'ASTRA_OFFICE_UI FAILED.*',log) or [''])[0]
    assert 'ASTRA_OFFICE_UI VERIFIED 40 frames research=7x2' in log,'Not every office tab was verified at all five sizes'
    sizes=('427x240','320x240','480x270','640x360','1280x720')
    for size in sizes:
        w,h=size.split('x')
        assert re.search(r'ASTRA_OFFICE_UI pass g'+size+r': \d+x\d+ window, GUI '+w+'x'+h+r' at scale \d',log),'The '+size+' pass did not run at its size'
    layouts=re.findall(r'ASTRA_OFFICE_UI layout tab (\d+) g(\d+x\d+) section=\d+ screen=\S+ problems=\[(.*)\]',log)
    assert len(layouts)==40 and {(t,g) for t,g,_ in layouts}=={(str(t),g) for t in range(8) for g in sizes},'Expected 40 layout checks, found %d'%len(layouts)
    bad=[(tab,size,problems) for tab,size,problems in layouts if problems.strip()]
    assert not bad,'Layout problems: %s'%bad[:3]
    assert re.search(r'ASTRA_OFFICE_UI overview jump 7->\d+',log),'The overview jump was not checked'
    assert re.search(r'ASTRA_OFFICE_UI overview trend coins=-?\d+ span=\d+ shown',log),'The treasury trend was not shown'
    oks=re.findall(r'ASTRA_OFFICE_UI research (\w+) ok (g\d+x\d+)',log)
    want={(n,g) for n in ('wheel','density','keys','next','search','group','cuts') for g in ('g427x240','g1280x720')}
    assert len(oks)==14 and set(oks)==want,'Expected 14 research scenarios, found %s'%sorted(set(oks))
    shots=re.findall(r'ASTRA_OFFICE_UI screenshot tab \d+ (\S+\.png)',log)
    assert len(shots)==40 and len(set(shots))==40,'Expected forty distinct office frames'
    research=re.findall(r'ASTRA_OFFICE_UI screenshot research \w+ (\S+\.png)',log)
    assert len(research)==14 and len(set(research))==14,'Expected fourteen distinct research frames'
    shots=shots+research
    for shot in shots:
        raw=(GAME_DIR/shot).resolve().read_bytes()
        assert raw[:8]==bytes.fromhex('89504e470d0a1a0a') and len(raw)>10000,'Office frame is not a real PNG: '+shot
    print(json.dumps({'status':'PASSED','mode':args.mode,'frames':shots},indent=2));raise SystemExit(0)
assert 'ASTRA_AUTONOMY FAILED' not in log,'Autonomy harness failed'
assert 'ASTRA_TRADE FAILED' not in log,'Trade harness failed'
assert 'ASTRA_ATLAS FAILED' not in log,'Atlas harness failed'
assert 'ASTRA_ROAD FAILED' not in log,'Road harness failed'
assert 'ASTRA_CARAVAN FAILED' not in log,'Caravan harness failed'
assert 'ASTRA_QUEST FAILED' not in log,'Quest harness failed'
assert 'ASTRA_CAMP FAILED' not in log,'Camp harness failed'
if args.mode=='autonomy':
 lines=[line for line in log.splitlines() if 'ASTRA_AUTONOMY VERIFIED' in line]
 assert len(lines)==1 and f"reload={str(args.reload).lower()}" in lines[0] and 'BUILD SUCCESSFUL' in log,'Missing verified autonomous construction'
 assert 'ASTRA_AUTONOMY mayor approved' in log,'No autonomous approval recorded'
elif args.mode=='quarry':
 lines=[line for line in log.splitlines() if 'ASTRA_QUARRY VERIFIED' in line]
 assert len(lines)==1 and 'reload=false' in lines[0] and 'BUILD SUCCESSFUL' in log,'Missing verified quarry run'
 numbers=re.search(r'taken=(\d+) stocked=(\d+)',lines[0])
 emptied=re.search(r'emptied=(\d+)',lines[0])
 assert numbers and int(numbers.group(1))>=4,'The quarry took too few blocks to prove anything'
 assert emptied and int(emptied.group(1))>=4,'The blocks did not leave the world'
 assert 'goals=QuarryGoal' in lines[0],'The miner was not working the quarry'
 paths=re.findall(r'ASTRA_QUARRY screenshot (.+)',log)
 assert len(paths)==1,'Expected the pit capture'
 frame=GAME_DIR/paths[0].strip()
 assert frame.resolve().is_relative_to(Path(__file__).resolve().parents[1]/'docs/runs') and frame.is_file() and frame.stat().st_size>10000,'Missing actual capture'
elif args.mode=='level':
 lines=[line for line in log.splitlines() if 'ASTRA_LEVEL VERIFIED' in line]
 assert len(lines)==1 and 'reload=false' in lines[0] and 'BUILD SUCCESSFUL' in log,'Missing verified building level'
 assert 'level=2 kept=2' in lines[0],'The workshop did not really reach level two'
 assert 'ASTRA_LEVEL progress' in log,'The run was not sampled'
 paths=re.findall(r'ASTRA_LEVEL screenshot (.+)',log)
 assert len(paths)==2,'Expected the tab and the finished work captures'
 for path in paths:
  frame=GAME_DIR/path.strip()
  assert frame.resolve().is_relative_to(Path(__file__).resolve().parents[1]/'docs/runs') and frame.is_file() and frame.stat().st_size>10000,'Missing actual capture'
elif args.mode=='cart-recovery':
 assert 'ASTRA_RECOVER FAILED' not in log,'Cart recovery harness failed'
 lines=[line for line in log.splitlines() if 'ASTRA_RECOVER VERIFIED' in line]
 assert len(lines)==1 and 'reload=false' in lines[0] and 'BUILD SUCCESSFUL' in log,'Missing verified cart recovery'
 assert 'fetch=closed' in lines[0] and 'cartAtYard=empty' in lines[0],'The cart did not come home empty'
 assert 'ASTRA_RECOVER the mayor had the cart left' in log,'The cart was never left on the road'
 paths=re.findall(r'ASTRA_RECOVER screenshot (.+)',log)
 assert len(paths)==2,'Expected the road and home captures'
 for path in paths:
  frame=GAME_DIR/path.strip()
  assert frame.resolve().is_relative_to(Path(__file__).resolve().parents[1]/'docs/runs') and frame.is_file() and frame.stat().st_size>10000,'Missing actual capture'
elif args.mode=='drill':
 assert 'ASTRA_DRILL FAILED' not in log,'Drill harness failed'
 lines=[line for line in log.splitlines() if 'ASTRA_DRILL VERIFIED' in line]
 assert len(lines)==1 and 'reload=false' in lines[0] and 'BUILD SUCCESSFUL' in log,'Missing verified drill'
 assert 'military=true' in lines[0] and 'atGround=true' in lines[0],'The recruit was not trained on the drill ground'
 post=re.search(r'profession=(\w+)',lines[0])
 assert post and post.group(1) in ('GUARD','ARCHER_GUARD','SOLDIER'),'The trained recruit got no military post'
 paths=re.findall(r'ASTRA_DRILL screenshot (.+)',log)
 assert len(paths)==1,'Expected the drill ground capture'
 frame=GAME_DIR/paths[0].strip()
 assert frame.resolve().is_relative_to(Path(__file__).resolve().parents[1]/'docs/runs') and frame.is_file() and frame.stat().st_size>10000,'Missing actual capture'
elif args.mode=='wall':
 assert 'ASTRA_WALL FAILED' not in log,'Wall harness failed'
 lines=[line for line in log.splitlines() if 'ASTRA_WALL VERIFIED' in line]
 assert len(lines)==1 and 'reload=false' in lines[0] and 'BUILD SUCCESSFUL' in log,'Missing verified castle wall'
 built=re.search(r'wall built=(\d+)/(\d+)',lines[0])
 assert built and int(built.group(2))>=100 and int(built.group(1))==int(built.group(2)),'The wall does not stand whole'
 assert 'guard_on_tower' in lines[0],'The archer is not on the tower'
 assert 'ASTRA_WALL the mayor ordered' in log and 'ASTRA_WALL progress' in log,'The run was not ordered or not sampled'
 paths=re.findall(r'ASTRA_WALL screenshot (.+)',log)
 assert len(paths)==2,'Expected the wall and the tower captures'
 for path in paths:
  frame=GAME_DIR/path.strip()
  assert frame.resolve().is_relative_to(Path(__file__).resolve().parents[1]/'docs/runs') and frame.is_file() and frame.stat().st_size>10000,'Missing actual capture'
elif args.mode=='wall-ring':
 # AD-127: the wall fitted to the village, built whole, then extended with the old stretch taken down and its stone returned.
 assert 'ASTRA_WALLRING FAILED' not in log,'Wall ring harness failed'
 lines=[line for line in log.splitlines() if 'ASTRA_WALLRING VERIFIED' in line]
 assert len(lines)==1 and 'reload=false' in lines[0] and 'BUILD SUCCESSFUL' in log,'Missing verified fitted wall'
 fitted=re.search(r'fitted built=(\d+)/(\d+) gates=(\d+) towers=(\d+) straight=(\d+) limit=(\d+)',lines[0])
 assert fitted and int(fitted.group(1))==int(fitted.group(2)) and int(fitted.group(2))>=150,'The fitted wall does not stand whole'
 assert int(fitted.group(3))>=1 and int(fitted.group(5))<=int(fitted.group(6)),'No gate or a straight stretch longer than the rounding allows'
 extended=re.search(r'extended built=(\d+)/(\d+) .*retired=(\d+) left=(\d+) returned=(\d+) cells=\d+ retiring=(\d+)',lines[0])
 assert extended and int(extended.group(1))==int(extended.group(2)),'The extended wall does not stand whole'
 assert int(extended.group(3))>=1 and int(extended.group(4))==0 and int(extended.group(5))>=1 and int(extended.group(6))==0,'The old stretch was not taken down and returned'
 paths=re.findall(r'ASTRA_WALLRING screenshot (.+)',log)
 assert len(paths)==2,'Expected the fitted and the extended captures'
 for path in paths:
  frame=GAME_DIR/path.strip()
  assert frame.resolve().is_relative_to(Path(__file__).resolve().parents[1]/'docs/runs') and frame.is_file() and frame.stat().st_size>10000,'Missing actual capture'
elif args.mode=='relocate':
 # AD-125: a building moved through the atlas by the village's builders; same record, level, core and chest, nothing lost or dropped.
 assert 'ASTRA_RELOCATE FAILED' not in log,'Relocation harness failed'
 lines=[line for line in log.splitlines() if 'ASTRA_RELOCATE VERIFIED' in line]
 assert len(lines)==1 and 'BUILD SUCCESSFUL' in log,'Missing verified relocation'
 line=lines[0]
 ops=re.search(r'ops=(\d+)/(\d+)',line);assert ops and int(ops.group(2))>0 and ops.group(1)==ops.group(2),'Not every operation was done'
 for key in ('moved=true','chest=same','lost=0','dropped=0','home_evictions=0','reload=true'):assert key in line,'Missing '+key
 lv=re.search(r'level=(\d+)->(\d+)',line);gr=re.search(r'grade=(\d+)->(\d+)',line)
 assert lv and lv.group(1)==lv.group(2),'The level changed';assert gr and gr.group(1)==gr.group(2),'The core grade changed'
 assert 'ASTRA_RELOCATE the mayor ordered the move' in log and 'ASTRA_RELOCATE progress' in log,'The move was not ordered or not sampled'
 paths=re.findall(r'ASTRA_RELOCATE screenshot (.+)',log)
 assert len(paths)==3 and all(n in ''.join(paths) for n in ('relocate-plan','relocate-work','relocate-done')),'Expected the plan, work and done captures'
 for path in paths:
  frame=GAME_DIR/path.strip()
  assert frame.resolve().is_relative_to(Path(__file__).resolve().parents[1]/'docs/runs') and frame.is_file() and frame.stat().st_size>10000,'Missing actual capture'
elif args.mode=='starter-food':
 # AD-104 P2: two days of a starter village on its own level-I field, 10 bread at the start, nobody missing a meal.
 assert 'ASTRA_STARTER_FOOD FAILED' not in log,'Starter food harness failed'
 lines=[line for line in log.splitlines() if 'ASTRA_STARTER_FOOD VERIFIED' in line]
 assert len(lines)==1 and 'reload=false' in lines[0] and 'BUILD SUCCESSFUL' in log,'Missing verified starter food run'
 numbers=re.search(r'reaped=(\d+) baked=(\d+) missed=(\d+)',lines[0])
 assert numbers,'The verified line lacks its counts'
 assert int(numbers.group(1))>=45,'The farmer reaped too few plots to feed the village'
 assert int(numbers.group(2))>=10,'Too little bread was baked by hand'
 assert int(numbers.group(3))==0,'A meal was missed'
 assert 'ASTRA_STARTER_FOOD fixture' in log and 'ASTRA_STARTER_FOOD progress' in log,'The run had no fixture or was not sampled'
 paths=re.findall(r'ASTRA_STARTER_FOOD screenshot (.+)',log)
 assert len(paths)==1,'Expected the field capture'
 frame=GAME_DIR/paths[0].strip()
 assert frame.resolve().is_relative_to(Path(__file__).resolve().parents[1]/'docs/runs') and frame.is_file() and frame.stat().st_size>10000,'Missing actual capture'
elif args.mode=='remote-village':
 # AD-111: a relevant starter village a day alone with the player 2000 blocks away eats its real bread through touch-loaded border chunks.
 assert 'ASTRA_REMOTE FAILED' not in log,'Remote village harness failed: '+(re.findall(r'ASTRA_REMOTE FAILED.*',log) or [''])[0]
 lines=[line for line in log.splitlines() if 'ASTRA_REMOTE VERIFIED' in line]
 assert len(lines)==1 and 'reload=false' in lines[0] and 'BUILD SUCCESSFUL' in log,'Missing verified remote village run'
 v=dict(re.findall(r'(\w+)=(\S+)',lines[0].split('ASTRA_REMOTE VERIFIED',1)[1]))
 for key in ('residents','dues','bread','rations','loads','deferred','maxTouchMs','avgMspt','samples','unloadedSamples','crops','grown'):assert key in v,'The verified line lacks '+key
 residents,dues=int(v['residents']),int(v['dues'])
 assert residents>=1 and dues==2*residents,'Not exactly two meals a resident'
 b0,b1=map(int,v['bread'].split('->'));r0,r1=map(int,v['rations'].split('->'))
 assert b0-b1==dues and r0-r1==5*dues,'The pantry did not lose exactly the meals eaten'
 assert int(v['loads'])>=1,'No pantry chunk was touch-loaded'
 assert int(v['unloadedSamples'])>=1 and int(v['grown'])==0,'The hall never unloaded or crops grew in a chunk that does not tick'
 assert float(v['avgMspt'])<50,'The server was too slow'
 assert 'ASTRA_REMOTE fixture' in log and 'ASTRA_REMOTE away' in log and 'ASTRA_REMOTE unloaded' in log,'The run had no fixture, departure or unload'
 samples=re.findall(r'ASTRA_REMOTE sample active=(\d+)/(\d+) mspt=(\S+) loads=(\d+) deferred=(\d+) maxTouchMs=(\S+) hallLoaded=(true|false) hallTicking=(true|false) missed=\[(.*?)\]',log)
 assert len(samples)>=19,'Expected a sample every 1200 active ticks, found %d'%len(samples)
 last=samples[-1]
 assert last[7]=='false' and all(x in ('0','') for x in last[8].split(',')),'The last sample shows a ticking hall or a missed meal'
 assert all(s[7]=='false' for s in samples),'The hall chunk ticked with the player away'
 print(json.dumps({'status':'PASSED','mode':args.mode,'verified':v,'samples':len(samples),'last_sample':{'mspt':float(last[2]),'loads':int(last[3]),'deferred':int(last[4]),'maxTouchMs':float(last[5])},'log':str(args.log)},indent=2));raise SystemExit(0)
elif args.mode=='map':
 lines=[line for line in log.splitlines() if 'ASTRA_MAP VERIFIED' in line]
 assert len(lines)==1 and 'reload=false' in lines[0] and 'BUILD SUCCESSFUL' in log,'Missing verified map run'
 assert 'following=true' in lines[0],'The map did not follow a resident'
 faces=re.search(r'faces=(\d+)',lines[0])
 assert faces and int(faces.group(1))>=1,'No resident face was drawn on the map'
 column=re.search(r'column=(\d+) blocks=(\d+)',lines[0])
 assert column and int(column.group(1))>=16,'The map does not hold the whole column'
 chunk=re.search(r'chunk=(\d+)',lines[0])
 assert chunk and int(chunk.group(1))>256,'The isolated chunk showed only its tops'
 canopy=re.search(r'canopy=(\d+)->(\d+)',lines[0])
 assert canopy and int(canopy.group(2))<int(canopy.group(1)),'Dropping the canopy revealed nothing under it'
 walk=re.search(r'walked (.+?) -> (.+?) zoom',lines[0])
 assert walk and walk.group(1)!=walk.group(2),'The resident never moved on the map'
 zoom=re.search(r'zoom ([\d.]+) -> ([\d.]+)',lines[0])
 assert zoom and float(zoom.group(2))>float(zoom.group(1)),'The map did not zoom'
 paths=re.findall(r'ASTRA_MAP screenshot (.+)',log)
 assert len(paths)==6,'Expected village, people, column, chunk, follow and ground captures'
 clearing=re.search(r'ASTRA_VILLAGE_CLEARING removed (\d+) tree blocks',log)
 assert clearing,'The village territory was not cleared of trees'
 doors=re.search(r'entrances=(\d+)/(\d+)',lines[0])
 assert doors and int(doors.group(2))>=6 and doors.group(1)==doors.group(2),'A generated building cannot be walked into'
 trees=re.search(r'trees on territory=(\d+) road cells=(\d+)',lines[0])
 assert trees and int(trees.group(1))==0 and int(trees.group(2))>0,'Trees are left on the village territory'
 steep=re.search(r'steep road steps=(\d+)',lines[0])
 assert steep and int(steep.group(1))==0,'A generated road climbs more than one block between neighbouring blocks'
 for path in paths:
  frame=GAME_DIR/path.strip()
  assert frame.resolve().is_relative_to(Path(__file__).resolve().parents[1]/'docs/runs') and frame.is_file() and frame.stat().st_size>10000,'Missing actual capture'
elif args.mode=='maptools':
 lines=[line for line in log.splitlines() if 'ASTRA_MAPTOOLS VERIFIED' in line]
 assert len(lines)==1 and 'reload=false' in lines[0] and 'BUILD SUCCESSFUL' in log and 'ASTRA_MAPTOOLS FAILED' not in log,'Missing verified map tools run'
 assert 'house=ordered' in lines[0],'The house was not ordered from the map'
 split=re.search(r'clearing builders=(\d+) miners=(\d+) level=(-?\d+) floor=(-?\d+)',lines[0])
 assert split and int(split.group(1))>0 and int(split.group(2))>0 and int(split.group(3))>=int(split.group(4))-1,'The clearing did not split soil and stone above the floor'
 crews=re.search(r'project soft=(\d+) stone=(\d+) excavation stone=(\d+) soft=(\d+)',lines[0])
 assert crews and int(crews.group(1))>0 and int(crews.group(2))==0 and int(crews.group(3))>0 and int(crews.group(4))==0,'Stone went to builders or soil to miners'
 road=re.search(r'road cells=(\d+) blocked=(\d+) busy=(\w+) road=(\w+)',lines[0])
 assert road and int(road.group(1))>0 and road.group(4)=='busy_builders','The road dry run or its honest refusal is missing'
 view=re.search(r'floor\+1=(-?\d+) after=(\d+)',lines[0])
 assert view and int(view.group(2))>0,'The floor height or the after view of the plan is missing'
 wall=re.search(r'wall=round towers=(\d+) gates=(\d+)',lines[0])
 assert 'wall square r=' in lines[0] and wall and int(wall.group(1))>=4 and int(wall.group(2))>=1,'The wall tool dry runs are missing'
 paths=re.findall(r'ASTRA_MAPTOOLS screenshot (.+)',log)
 assert len(paths)==5,'Expected house, house after, clearing, road and wall captures'
 for path in paths:
  frame=GAME_DIR/path.strip()
  assert frame.resolve().is_relative_to(Path(__file__).resolve().parents[1]/'docs/runs') and frame.is_file() and frame.stat().st_size>10000,'Missing actual capture'
elif args.mode=='raid':
 lines=[line for line in log.splitlines() if 'ASTRA_RAID VERIFIED' in line]
 assert len(lines)==1 and 'reload=false' in lines[0] and 'BUILD SUCCESSFUL' in log and 'ASTRA_RAID FAILED' not in log,'Missing verified raid run'
 facts=re.search(r'warning in chat=(\w+) spawned=(\d+) sheltering=(\d+) outcome=(\w+) recorded=(\w+) lost=(\d+) end in chat=(\w+)',lines[0])
 assert facts and facts.group(1)=='true' and int(facts.group(2))>0 and int(facts.group(3))>0,'No warning, no raiders or nobody took cover'
 assert facts.group(4)=='repelled' and facts.group(5)=='repelled' and facts.group(7)=='true','The raid did not end as beaten off with a message'
 paths=re.findall(r'ASTRA_RAID screenshot (.+)',log)
 assert len(paths)==1,'Expected the alarm capture'
 for path in paths:
  frame=GAME_DIR/path.strip()
  assert frame.resolve().is_relative_to(Path(__file__).resolve().parents[1]/'docs/runs') and frame.is_file() and frame.stat().st_size>10000,'Missing actual capture'
elif args.mode=='annex':
 lines=[line for line in log.splitlines() if 'ASTRA_ANNEX VERIFIED' in line]
 assert len(lines)==1 and 'reload=false' in lines[0] and 'BUILD SUCCESSFUL' in log and 'ASTRA_ANNEX FAILED' not in log,'Missing verified annex run'
 # The parent's levels from the one the annex needs (annexes.json parent_level: III for the lean-tos, II for the mill) up to VI.
 seen=re.search(r'linked=true turned=true intact=true beside hut levels \[([0-9, ]+)\]',lines[0])
 levels=[int(n) for n in seen.group(1).split(',')] if seen else []
 assert levels and levels==list(range(levels[0],7)) and levels[0]>=2,'The annex was not built, linked, turned and whole beside every hut level: '+lines[0]
 paths=re.findall(r'ASTRA_ANNEX screenshot (.+)',log)
 assert len(paths)==1+len(levels),'Expected the card and every parent level'
 for path in paths:
  frame=GAME_DIR/path.strip()
  assert frame.resolve().is_relative_to(Path(__file__).resolve().parents[1]/'docs/runs') and frame.is_file() and frame.stat().st_size>10000,'Missing actual capture'
elif args.mode=='restaurant':
 # AD-139: diners seated in the hall (their live "dining" status), the hall served, a courier fed a miner at his work, levels I, II, IV, VI read
 # as laid, the baking.4 card without «труд 80», the dog honestly planned until the livestock work provides dogs.
 lines=[line for line in log.splitlines() if 'ASTRA_RESTAURANT VERIFIED' in line]
 assert len(lines)==1 and 'reload=false' in lines[0] and 'BUILD SUCCESSFUL' in log and 'ASTRA_RESTAURANT FAILED' not in log,'Missing verified restaurant run'
 facts=re.search(r'seated=(\d+) served=(\d+) courierFed=(\w+) levels=\[([\d, ]+)\] seats=(\d+) .* labourWords=(\w+) dog=(\w+)',lines[0])
 assert facts and int(facts.group(1))>=3 and int(facts.group(2))>=3 and facts.group(3)=='true','Fewer than three seated or served, or the courier fed nobody: '+lines[0]
 assert facts.group(4).replace(' ','')=='1,2,4,6' and facts.group(5)=='16','The levels did not read as laid or level VI does not seat 16: '+lines[0]
 assert facts.group(6)=='false' and facts.group(7) in ('planned','provided'),'The research card still says labour or the dog is not reported: '+lines[0]
 paths=re.findall(r'ASTRA_RESTAURANT screenshot (.+)',log)
 assert len(paths)==5 and all(any(p.strip().endswith('-restaurant-'+k+'.png') for p in paths) for k in ('level1','hall','courier','level4','level6')),'Expected level1, hall, courier, level4 and level6 frames'
 for path in paths:
  frame=GAME_DIR/path.strip()
  assert frame.resolve().is_relative_to(Path(__file__).resolve().parents[1]/'docs/runs') and frame.is_file() and frame.stat().st_size>10000,'Missing actual capture'
elif args.mode=='housing-beds':
 lines=[line for line in log.splitlines() if 'ASTRA_HOUSING_BEDS VERIFIED' in line]
 assert len(lines)==1 and 'reload=false' in lines[0] and 'BUILD SUCCESSFUL' in log and 'ASTRA_HOUSING_BEDS FAILED' not in log,'Missing verified housing beds run'
 facts=re.search(r'every one of (\d+) people asleep in a bed of their own: home@3=(\d+)/(\d+) home_2@6=(\d+)/(\d+) upstairs=(\d+)',lines[0])
 assert facts and facts.group(1)=='14' and facts.group(2)==facts.group(3)=='4' and facts.group(4)==facts.group(5)=='10' and facts.group(6)=='6','The ladder beds were not all slept in: '+lines[0]
 paths=re.findall(r'ASTRA_HOUSING_BEDS screenshot (.+)',log)
 assert len(paths)==3,'Expected three interior captures'
 for path in paths:
  frame=GAME_DIR/path.strip()
  assert frame.resolve().is_relative_to(Path(__file__).resolve().parents[1]/'docs/runs') and frame.is_file() and frame.stat().st_size>10000,'Missing actual capture'
elif args.mode=='framed-window':
 # AD-140: the framed windows of all eleven woods in a wall by a door, every block and item model real; joined windows (owner 2026-09-24):
 # a 3x2, a 2x2 and an L each joined as expected, the gallery's home laid with every window as designed and some joined; ten real frames.
 lines=[line for line in log.splitlines() if 'ASTRA_FRAMED_WINDOW VERIFIED' in line]
 assert len(lines)==1 and 'reload=false' in lines[0] and 'BUILD SUCCESSFUL' in log and 'ASTRA_FRAMED_WINDOW FAILED' not in log,'Missing verified framed window run'
 facts=re.search(r'placed=16 woods=11 joined=13/13 home=(\d+)/(\d+) homeJoined=(\d+) models=11x3',lines[0])
 assert facts and facts.group(1)==facts.group(2) and int(facts.group(3))>0,'Wall, joined groups or home not as expected: '+lines[0]
 paths=re.findall(r'ASTRA_FRAMED_WINDOW screenshot (.+)',log)
 assert len(paths)==10,'Expected ten captures'
 for path in paths:
  frame=GAME_DIR/path.strip()
  assert frame.resolve().is_relative_to(Path(__file__).resolve().parents[1]/'docs/runs') and frame.is_file() and frame.stat().st_size>10000,'Missing actual capture'
elif args.mode=='furniture':
 # AD-143: the 22 furniture models real, the player sat and stood with no seat left, residents seated on the restaurant's chairs at I, IV and
 # VI (by their seats), a resident resting on a chair at home, the levels read as laid, nine real frames.
 lines=[line for line in log.splitlines() if 'ASTRA_FURNITURE VERIFIED' in line]
 assert len(lines)==1 and 'reload=false' in lines[0] and 'BUILD SUCCESSFUL' in log and 'ASTRA_FURNITURE FAILED' not in log,'Missing verified furniture run'
 facts=re.search(r'placed=(\d+) models=22 playerSeated=(\w+) seated1=(\d+) seated4=(\d+) seated6=(\d+) rested=(\w+) levels=\[([\d, ]+)\]',lines[0])
 assert facts and int(facts.group(1))>=30 and facts.group(2)=='true' and facts.group(6)=='true','Showroom, seated player or resting resident missing: '+lines[0]
 assert int(facts.group(3))>=3 and int(facts.group(4))>=5 and int(facts.group(5))>=6 and facts.group(7).replace(' ','')=='1,1,4,6','Too few diners seated or levels not as laid: '+lines[0]
 paths=re.findall(r'ASTRA_FURNITURE screenshot (.+)',log)
 assert len(paths)==9 and all(any(p.strip().endswith('-furniture-'+k+'.png') for p in paths) for k in ('showroom','showroom-angle','seated','restaurant-1','restaurant-4','restaurant-6','house','porch','hotbar')),'Expected nine frames'
 for path in paths:
  frame=GAME_DIR/path.strip()
  assert frame.resolve().is_relative_to(Path(__file__).resolve().parents[1]/'docs/runs') and frame.is_file() and frame.stat().st_size>10000,'Missing actual capture'
elif args.mode=='autoyard':
 # AD-138 VI: the yard's machine filled a feeder, put meat in the kennel's bin and had a wolf cull the surplus sheep, its mutton in the chest.
 lines=[line for line in log.splitlines() if 'ASTRA_AUTOYARD VERIFIED' in line]
 assert len(lines)==1 and 'reload=false' in lines[0] and 'BUILD SUCCESSFUL' in log and 'ASTRA_AUTOYARD FAILED' not in log,'Missing verified auto yard run'
 m=re.search(r'filled=(\d+) pen1=(\d+) bin=(\d+) gathered=\d+ lying=(\d+) sawDrop=true',lines[0])
 assert m and int(m.group(1))>=1 and int(m.group(2))<=16 and int(m.group(3))>=1 and m.group(4)=='0','The machine did not fill, bin, cull and clear the drops: '+lines[0]
 paths=re.findall(r'ASTRA_AUTOYARD screenshot (.+)',log)
 assert len(paths)>=1 and paths[-1].strip().endswith('-autoyard-after.png'),'Expected the yard after'
 for path in paths:
  frame=GAME_DIR/path.strip()
  assert frame.resolve().is_relative_to(Path(__file__).resolve().parents[1]/'docs/runs') and frame.is_file() and frame.stat().st_size>10000,'Missing actual capture'
elif args.mode=='grazing':
 # AD-138 V: a hungry yard V grazes its herd on the village grass by day; at night the herd is home, the gates shut, the wolves in bed.
 lines=[line for line in log.splitlines() if 'ASTRA_GRAZING VERIFIED' in line]
 assert len(lines)==1 and 'reload=false' in lines[0] and 'BUILD SUCCESSFUL' in log and 'ASTRA_GRAZING FAILED' not in log,'Missing verified grazing run'
 day=re.search(r'day out=(\d+) onGrass=(\d+) home=\d+/(\d+) gates=1open 2open',lines[0]);night=re.search(r'night out=0 onGrass=0 home=(\d+)/(\d+) gates=1shut 2shut wolvesInBed=2',lines[0])
 assert day and int(day.group(1))>=3 and int(day.group(2))>=3 and night and night.group(1)==night.group(2),'The herd did not graze by day and come home at night: '+lines[0]
 paths=re.findall(r'ASTRA_GRAZING screenshot (.+)',log)
 assert len(paths)==2 and paths[0].strip().endswith('-grazing-out.png') and paths[1].strip().endswith('-grazing-home.png'),'Expected the day and the night frame'
 for path in paths:
  frame=GAME_DIR/path.strip()
  assert frame.resolve().is_relative_to(Path(__file__).resolve().parents[1]/'docs/runs') and frame.is_file() and frame.stat().st_size>10000,'Missing actual capture'
elif args.mode=='wolves':
 # AD-138 IV: the kennel's wolves with their owner away — by day in the yard out of the pens, at night each on its own straw, fed from the bin.
 lines=[line for line in log.splitlines() if 'ASTRA_WOLVES VERIFIED' in line]
 assert len(lines)==1 and 'reload=false' in lines[0] and 'BUILD SUCCESSFUL' in log and 'ASTRA_WOLVES FAILED' not in log,'Missing verified wolves run'
 assert 'day day good=2/2' in lines[0] and 'night night good=2/2 beef=2' in lines[0],'The wolves did not keep the yard by day and their beds at night, fed: '+lines[0]
 paths=re.findall(r'ASTRA_WOLVES screenshot (.+)',log)
 assert len(paths)==2 and paths[0].strip().endswith('-wolves-day.png') and paths[1].strip().endswith('-wolves-night.png'),'Expected the day and the night frame'
 for path in paths:
  frame=GAME_DIR/path.strip()
  assert frame.resolve().is_relative_to(Path(__file__).resolve().parents[1]/'docs/runs') and frame.is_file() and frame.stat().st_size>10000,'Missing actual capture'
elif args.mode=='wolf-rescue':
 # AD-150: the poachers' camp with its cages, the greys coaxed out with meat, and the kennel taking them in as the village's own.
 lines=[line for line in log.splitlines() if 'ASTRA_WOLFRESCUE VERIFIED' in line]
 assert len(lines)==1 and 'BUILD SUCCESSFUL' in log and 'ASTRA_WOLFRESCUE FAILED' not in log,'Missing verified wolf rescue'
 m=re.search(r'cages=(\d+) freed=(\d+) enlisted=(\d+) asked=(\d+)',lines[0])
 assert m and int(m.group(1))>=1 and int(m.group(2))==int(m.group(1)) and int(m.group(3))==int(m.group(2)) and int(m.group(3))>=int(m.group(4)),'The greys did not come home: '+lines[0]
 shots=[line.split('screenshot ')[1].strip() for line in log.splitlines() if 'ASTRA_WOLFRESCUE screenshot' in line]
 assert len(shots)==3,'Three frames: the camp, the freed greys, the kennel (%s)'%len(shots)
 print(json.dumps({'status':'PASSED','mode':args.mode,'frames':shots}));raise SystemExit(0)
elif args.mode=='quest-talk':
 # AD-149: the errand is asked for and taken in the conversation window, and the board itself says whose it is.
 lines=[line for line in log.splitlines() if 'ASTRA_QUESTTALK VERIFIED' in line]
 assert len(lines)==1 and 'state=taken' in lines[0] and 'owner=mine' in lines[0] and 'BUILD SUCCESSFUL' in log and 'ASTRA_QUESTTALK FAILED' not in log,'Missing verified quest talk'
 shots=[line.split('screenshot ')[1].strip() for line in log.splitlines() if 'ASTRA_QUESTTALK screenshot' in line]
 assert len(shots)==3,'Three frames: the greeting, the errand, the thanks (%s)'%len(shots)
 print(json.dumps({'status':'PASSED','mode':args.mode,'frames':shots}));raise SystemExit(0)
elif args.mode=='dialog':
 # AD-146: the conversation window — a greeting with the village's name, «Как дела?» answered in the window, trade opening the card.
 lines=[line for line in log.splitlines() if 'ASTRA_DIALOG VERIFIED' in line]
 assert len(lines)==1 and 'reload=false' in lines[0] and 'card=true' in lines[0] and 'BUILD SUCCESSFUL' in log and 'ASTRA_DIALOG FAILED' not in log,'Missing verified dialog run'
 greeting=re.search(r'greeting=\[(.*?)\] news=\[(.*?)\]',lines[0])
 assert greeting and greeting.group(1).strip() and greeting.group(2).count('|')>=2,'No greeting or no news in the window: '+lines[0]
 paths=re.findall(r'ASTRA_DIALOG screenshot (.+)',log)
 assert len(paths)==3,'Expected the greeting, the news and the card'
 for path in paths:
  frame=GAME_DIR/path.strip()
  assert frame.resolve().is_relative_to(Path(__file__).resolve().parents[1]/'docs/runs') and frame.is_file() and frame.stat().st_size>10000,'Missing actual capture'
elif args.mode=='medicine':
 # AD-152: two sick residents walked to the hospital of level I; its one bed cured one for one bandage, the other waits.
 lines=[line for line in log.splitlines() if 'ASTRA_MEDICINE VERIFIED' in line]
 assert len(lines)==1 and 'reload=false' in lines[0] and 'BUILD SUCCESSFUL' in log and 'ASTRA_MEDICINE FAILED' not in log,'Missing verified medicine run'
 assert 'arrived=2 cured=1 bandages=2 beds=1' in lines[0],'The hospital did not take in both and cure one: '+lines[0]
 paths=re.findall(r'ASTRA_MEDICINE screenshot (.+)',log)
 assert len(paths)==1 and paths[0].strip().endswith('-medicine-hospital.png'),'Expected the hospital frame'
elif args.mode=='construction-ladder':
 # AD-153: Construction IV - four builders of the hall build one funded cottage together, at least three of them setting its blocks.
 lines=[line for line in log.splitlines() if 'ASTRA_CREW VERIFIED' in line]
 assert len(lines)==1 and 'reload=false' in lines[0] and 'BUILD SUCCESSFUL' in log and 'ASTRA_CREW FAILED' not in log,'Missing verified crew run'
 m=re.search(r'builders=(\d+) by=(\d+)',lines[0]);assert m and int(m.group(1))==4 and int(m.group(2))>=3 and 'reach=2' in lines[0],'The crew is not as the ladder says: '+lines[0]
 paths=re.findall(r'ASTRA_CREW screenshot (.+)',log)
 assert len(paths)==1 and paths[0].strip().endswith('-crew-site.png'),'Expected the crew on the site'
 frame=GAME_DIR/paths[0].strip()
 assert frame.resolve().is_relative_to(Path(__file__).resolve().parents[1]/'docs/runs') and frame.is_file() and frame.stat().st_size>10000,'Missing actual capture'
elif args.mode=='school':
 # AD-151: schools I, IV, VI laid; the VI school's class took five children and its military class the two eldest.
 lines=[line for line in log.splitlines() if 'ASTRA_SCHOOL VERIFIED' in line]
 assert len(lines)==1 and 'reload=false' in lines[0] and 'BUILD SUCCESSFUL' in log and 'ASTRA_SCHOOL FAILED' not in log,'Missing verified school run'
 assert 'station=true taught=5 cadets=2' in lines[0] and 'seats=8' in lines[0],'The class or the military class is not as the ladder says: '+lines[0]
 paths=re.findall(r'ASTRA_SCHOOL screenshot (.+)',log)
 assert len(paths)==2 and paths[0].strip().endswith('-school-levels.png') and paths[1].strip().endswith('-school-lesson.png'),'Expected the schools and the lesson'
 for path in paths:
  frame=GAME_DIR/path.strip()
  assert frame.resolve().is_relative_to(Path(__file__).resolve().parents[1]/'docs/runs') and frame.is_file() and frame.stat().st_size>10000,'Missing actual capture'
elif args.mode=='livestock':
 lines=[line for line in log.splitlines() if 'ASTRA_LIVESTOCK VERIFIED' in line]
 assert len(lines)==1 and 'reload=false' in lines[0] and 'BUILD SUCCESSFUL' in log and 'ASTRA_LIVESTOCK FAILED' not in log,'Missing verified livestock run'
 facts=re.search(r'filled=(\d+) babies=(\d+) pigsInPen4=(\d+) gatesShut=(\w+) wet=0',lines[0])
 assert facts and facts.group(1)=='4' and int(facts.group(2))>0 and int(facts.group(3))>=2 and facts.group(4)=='true','The yard did not work: '+lines[0]
 paths=re.findall(r'ASTRA_LIVESTOCK screenshot (.+)',log)
 assert len(paths)==5 and any(p.strip().endswith('-livestock-drive.png') for p in paths),'Expected a drive, yard I, yard III, the feeder and the card'
 for path in paths:
  frame=GAME_DIR/path.strip()
  assert frame.resolve().is_relative_to(Path(__file__).resolve().parents[1]/'docs/runs') and frame.is_file() and frame.stat().st_size>10000,'Missing actual capture'
elif args.mode=='warehouse':
 # AD-147: the courier of II pulled its cart with a parcel and five stacks from the mine into the store (exactly the trip's items, the sum
 # unchanged), the VI store sorted itself, the card shows the store; the wolves honestly planned until the livestock work provides them.
 lines=[line for line in log.splitlines() if 'ASTRA_WAREHOUSE VERIFIED' in line]
 assert len(lines)==1 and 'reload=false' in lines[0] and 'BUILD SUCCESSFUL' in log and 'ASTRA_WAREHOUSE FAILED' not in log,'Missing verified warehouse run'
 facts=re.search(r'planned=(\d+) moved=(\d+) sum=(\d+) sorted=(\d+) slots=(\d+) carts=(\w+) wolves=(\w*)',lines[0])
 assert facts and facts.group(1)==facts.group(2) and int(facts.group(2))>4*64 and int(facts.group(4))>=3,'The trip or the sorting did not happen: '+lines[0]
 # 0.9.0: the kennel's source pulls carts (VillageDogs.pulls()), so a village without kennel wolves reports no_wolf instead of planned.
 assert facts.group(5)=='162' and facts.group(7) in ('planned','no_wolf',''),'The store of II is not 162 slots or the wolves are not reported: '+lines[0]
 paths=re.findall(r'ASTRA_WAREHOUSE screenshot (.+)',log)
 assert len(paths)==6 and all(any(p.strip().endswith('-warehouse-'+k+'.png') for p in paths) for k in ('cart','level1','level3','level6','sorted','card')),'Expected cart, level1, level3, level6, sorted and card frames'
 for path in paths:
  frame=GAME_DIR/path.strip()
  assert frame.resolve().is_relative_to(Path(__file__).resolve().parents[1]/'docs/runs') and frame.is_file() and frame.stat().st_size>10000,'Missing actual capture'
elif args.mode=='night':
 lines=[line for line in log.splitlines() if 'ASTRA_NIGHT VERIFIED' in line]
 assert len(lines)==1 and 'reload=false' in lines[0] and 'BUILD SUCCESSFUL' in log and 'ASTRA_NIGHT FAILED' not in log,'Missing verified night run'
 facts=re.search(r'madeWay=(\w+) stuck=(\w+) guard awake=(\w+) both got up at dawn=(\w+)',lines[0])
 assert facts and facts.group(2)=='false' and facts.group(3)=='true' and facts.group(4)=='true','Somebody stuck, the guard asleep or nobody up at dawn'
 paths=re.findall(r'ASTRA_NIGHT screenshot (.+)',log)
 assert len(paths)==1,'Expected the asleep capture'
 for path in paths:
  frame=GAME_DIR/path.strip()
  assert frame.resolve().is_relative_to(Path(__file__).resolve().parents[1]/'docs/runs') and frame.is_file() and frame.stat().st_size>10000,'Missing actual capture'
elif args.mode=='battle':
 lines=[line for line in log.splitlines() if 'ASTRA_BATTLE VERIFIED' in line]
 assert len(lines)==1 and 'reload=false' in lines[0] and 'BUILD SUCCESSFUL' in log and 'ASTRA_BATTLE FAILED' not in log,'Missing verified battle run'
 facts=re.search(r'soldier fought=(\w+) guard fought=(\w+) guard health ([\d.]+)->([\d.]+) soldier least ([\d.-]+) soldier fell=(\w+) army=(\w+) reason=(\w*) conquest first=(\w+) again=(\w+) owner=(\w+) mayor=(\w+)',lines[0])
 assert facts and facts.group(1)=='true' and facts.group(2)=='true' and float(facts.group(4))<float(facts.group(3)),'The soldier and the guard did not both fight, or the guard was never struck'
 assert facts.group(6)=='true' and facts.group(7)=='withdrawn' and facts.group(8)=='no_soldiers','The fallen soldier did not make the army withdraw'
 assert facts.group(9)=='ok' and facts.group(10)=='done' and facts.group(11)=='true' and facts.group(12)=='true','The conquest did not pass the settlement once to the victor'
 paths=re.findall(r'ASTRA_BATTLE screenshot (.+)',log)
 assert len(paths)==1,'Expected the fight capture'
 for path in paths:
  frame=GAME_DIR/path.strip()
  assert frame.resolve().is_relative_to(Path(__file__).resolve().parents[1]/'docs/runs') and frame.is_file() and frame.stat().st_size>10000,'Missing actual capture'
elif args.mode=='load':
 lines=[line for line in log.splitlines() if 'ASTRA_LOAD VERIFIED' in line]
 assert len(lines)==1 and 'reload=false' in lines[0] and 'BUILD SUCCESSFUL' in log,'Missing verified load run'
 numbers=re.search(r'(\d+) settlements ran (\d+) minutes together: settlements=(\d+) residents=(\d+) projects=(\d+) tick=([\d.]+)ms; worst tick ([\d.]+)ms',lines[0])
 assert numbers,'The load line carries no measurements'
 assert int(numbers.group(1))>=3 and int(numbers.group(4))>=16,'Too small a load to prove anything'
 assert float(numbers.group(7))<45,'Server tick budget exceeded'
 samples=[line for line in log.splitlines() if 'ASTRA_LOAD progress' in line]
 assert len(samples)>=10,'The run was not sampled through: '+str(len(samples))
 paths=re.findall(r'ASTRA_LOAD screenshot (.+)',log)
 assert len(paths)==1,'Expected one world capture'
 frame=GAME_DIR/paths[0].strip()
 assert frame.resolve().is_relative_to(Path(__file__).resolve().parents[1]/'docs/runs') and frame.is_file() and frame.stat().st_size>10000,'Missing actual capture'
elif args.mode=='war':
 lines=[line for line in log.splitlines() if 'ASTRA_WAR VERIFIED' in line]
 assert len(lines)==1 and 'reload=false' in lines[0] and 'BUILD SUCCESSFUL' in log,'Missing verified war desk'
 assert 'ASTRA_WAR desk neighbours=' in log,'The desk never listed a neighbour'
 numbers=re.search(r'fences (\d+) rations (\d+) state (\w+)',lines[0])
 assert numbers and int(numbers.group(1))>0 and int(numbers.group(2))>0 and numbers.group(3)!='none','The campaign carries no real supplies'
 paths=re.findall(r'ASTRA_WAR screenshot (.+)',log)
 assert len(paths)==2,'Expected desk and campaign captures'
 for path in paths:
  frame=GAME_DIR/path.strip()
  assert frame.resolve().is_relative_to(Path(__file__).resolve().parents[1]/'docs/runs') and frame.is_file() and frame.stat().st_size>10000,'Missing actual capture'
elif args.mode=='plan':
 lines=[line for line in log.splitlines() if 'ASTRA_PLAN VERIFIED' in line]
 assert len(lines)==1 and 'reload=false' in lines[0] and 'BUILD SUCCESSFUL' in log,'Missing verified atlas plan'
 assert 'ASTRA_PLAN estimate operations=' in log,'No estimate recorded'
 numbers=re.search(r'operations=(\d+) cells=(\d+) items=(\d+) shortage=(\d+)',log)
 assert numbers and int(numbers.group(1))>0 and int(numbers.group(3))>0,'Estimate has no work or materials'
 assert int(numbers.group(2))<=int(numbers.group(1)),'Unique cells cannot exceed operations'
 assert 'draft kept 1 of' in lines[0],'No draft was kept'
 turned=re.search(r'turned=(\d+) match=(\d+)/(\d+)',lines[0])
 assert turned and turned.group(1)=='1' and int(turned.group(2))>0,'The turn button did not bring back the design turned a quarter'
 assert 'office lost: tools off=true' in lines[0],'The open map kept its order tools after the office was lost'
 future=re.search(r'future=(\d+)',log)
 assert future and int(future.group(1))>0,'The estimate carried no future volume'
 paths=re.findall(r'ASTRA_PLAN screenshot (.+)',log)
 assert len(paths)==3,'Expected estimate, turned and draft captures'
 for path in paths:
  frame=GAME_DIR/path.strip()
  assert frame.resolve().is_relative_to(Path(__file__).resolve().parents[1]/'docs/runs') and frame.is_file() and frame.stat().st_size>10000,'Missing actual capture'
elif args.mode=='camp':
 lines=[line for line in log.splitlines() if 'ASTRA_CAMP VERIFIED' in line]
 assert len(lines)==1 and 'reload=false' in lines[0] and 'BUILD SUCCESSFUL' in log,'Missing verified camp escort'
 assert 'ASTRA_CAMP the expeditioner reported a real lead' in log and 'ASTRA_CAMP the companion follows after a real right click' in log,'Expedition or escort step missing'
 paths=re.findall(r'ASTRA_CAMP screenshot (.+)',log)
 assert len(paths)==2,'Expected camp and arrival captures'
 for path in paths:
  frame=GAME_DIR/path.strip()
  assert frame.resolve().is_relative_to(Path(__file__).resolve().parents[1]/'docs/runs') and frame.is_file() and frame.stat().st_size>10000,'Missing actual capture'
elif args.mode=='adventure':
 lines=[line for line in log.splitlines() if 'ASTRA_ADVENTURE VERIFIED' in line]
 assert len(lines)==1 and 'reload=false' in lines[0] and 'BUILD SUCCESSFUL' in log,'Missing verified adventure run'
 for kind in ('adit','barrow'):
  assert 'ASTRA_ADVENTURE built the %s in real ground'%kind in log,'The %s was not built in the generated world'%kind
 assert 'ASTRA_ADVENTURE the village posted the wrecked expedition at' in log,'The wrecked expedition was not posted by the village'
 assert 'ASTRA_ADVENTURE the village posted the stockade at' in log,'The stockade was not posted by the village'
 assert 'ASTRA_ADVENTURE the board drew a chart with a cross' in log,'The board handed out no chart with a cross'
 assert 'ASTRA_ADVENTURE a real bandage put a survivor back on their feet' in log,'No survivor was treated by hand'
 assert 'ASTRA_ADVENTURE the cage is broken open with a pickaxe' in log,'The cage was not broken open'
 assert 'ASTRA_ADVENTURE the chief is down after' in log,'The band of the stockade did not fall'
 done=re.search(r'quest=done (\d+)/(\d+).*coins=(\d+) reputation=(\d+)',lines[0])
 assert done and done.group(1)==done.group(2) and int(done.group(3))>0 and int(done.group(4))>0,'The captive quest did not pay coin and reputation'
 paths=re.findall(r'ASTRA_ADVENTURE screenshot (.+)',log)
 assert len(paths)==4,'Expected wreck, chart, stockade and reward captures'
 assert 'ASTRA_ADVENTURE the chart of the stockade carries its cross at' in log,'The chart of the stockade was not held up'
 walked=re.search(r'maxStep=([\d.]+)',lines[0])
 assert walked and float(walked.group(1))<=8,'The captive moved by a shortcut on the way home'
 for path in paths:
  frame=GAME_DIR/path.strip()
  assert frame.resolve().is_relative_to(Path(__file__).resolve().parents[1]/'docs/runs') and frame.is_file() and frame.stat().st_size>10000,'Missing actual capture'
elif args.mode=='wild':
 lines=[line for line in log.splitlines() if 'ASTRA_WILD VERIFIED' in line]
 assert len(lines)==1 and 'reload=false' in lines[0] and 'BUILD SUCCESSFUL' in log,'Missing verified wild quest run'
 for kind in ('hideout','collapse','den','tower'):
  assert 'ASTRA_WILD built the %s in real ground'%kind in log,'The %s was not built in the generated world'%kind
 for step in ('the fall was dug out with a real shovel','a real flint and steel lit the brazier','a crop quest opened','standing in the structure explored it'):
  assert 'ASTRA_WILD '+step in log,'Missing step: '+step
 paths=re.findall(r'ASTRA_WILD screenshot (.+)',log)
 assert len(paths)==2,'Expected tower and ruin captures'
 for path in paths:
  frame=GAME_DIR/path.strip()
  assert frame.resolve().is_relative_to(Path(__file__).resolve().parents[1]/'docs/runs') and frame.is_file() and frame.stat().st_size>10000,'Missing actual capture'
elif args.mode=='escort':
 assert 'ASTRA_ESCORT FAILED' not in log,'Escort harness failed'
 lines=[line for line in log.splitlines() if 'ASTRA_ESCORT VERIFIED' in line]
 assert len(lines)==1 and 'reload=false' in lines[0] and 'BUILD SUCCESSFUL' in log,'Missing verified escort run'
 for step in ('the companion waits for a boat without swimming','the companion boards the boat','the companion rowed'):
  assert 'ASTRA_ESCORT '+step in log,'Missing step: '+step
 step=re.search(r'maxStep=([\d.]+)',lines[0])
 assert step and float(step.group(1))<=8,'The companion moved by a shortcut'
 paths=re.findall(r'ASTRA_ESCORT screenshot (.+)',log)
 assert len(paths)==3,'Expected needs-boat, landed and nether captures'
 for path in paths:
  frame=GAME_DIR/path.strip()
  assert frame.resolve().is_relative_to(Path(__file__).resolve().parents[1]/'docs/runs') and frame.is_file() and frame.stat().st_size>10000,'Missing actual capture'
elif args.mode=='chain':
 assert 'ASTRA_CHAIN FAILED' not in log,'Chain harness failed'
 lines=[line for line in log.splitlines() if 'ASTRA_CHAIN VERIFIED' in line]
 assert len(lines)==1 and 'reload=false' in lines[0] and 'BUILD SUCCESSFUL' in log,'Missing verified chain run'
 for step in ("the village's tick built the war camp in real ground","the war camp was broken and paid","the village's tick built the crypt in real ground"):
  assert 'ASTRA_CHAIN '+step in log,'Missing step: '+step
 paths=re.findall(r'ASTRA_CHAIN screenshot (.+)',log)
 assert len(paths)==2,'Expected war camp and crypt captures'
 for path in paths:
  frame=GAME_DIR/path.strip()
  assert frame.resolve().is_relative_to(Path(__file__).resolve().parents[1]/'docs/runs') and frame.is_file() and frame.stat().st_size>10000,'Missing actual capture'
elif args.mode=='quest':
 lines=[line for line in log.splitlines() if 'ASTRA_QUEST VERIFIED' in line]
 assert len(lines)==1 and 'reload=false' in lines[0] and 'BUILD SUCCESSFUL' in log,'Missing verified quest completion'
 assert 'ASTRA_QUEST village posted a clearing quest by itself' in log,'Quest was not posted by the village itself'
 assert 'the quests tab away from the village shows' in log,'The inventory quests tab did not show the player their errand'
 paths=re.findall(r'ASTRA_QUEST screenshot (.+)',log)
 assert len(paths)==3,'Expected board, quests tab and completion captures'
 for path in paths:
  frame=GAME_DIR/path.strip()
  assert frame.resolve().is_relative_to(Path(__file__).resolve().parents[1]/'docs/runs') and frame.is_file() and frame.stat().st_size>10000,'Missing actual capture'
elif args.mode=='escort-reload':
 lines=[line for line in log.splitlines() if 'ASTRA_ESCORT VERIFIED after the game was closed' in line]
 assert len(lines)==1 and 'reload=true' in lines[0] and 'BUILD SUCCESSFUL' in log and 'ASTRA_ESCORT FAILED' not in log,'Missing verified companion after a reload'
 assert 'in minecraft:the_nether' in lines[0],'The companion is not in the world it crossed into'
elif args.mode=='animal':
 lines=[line for line in log.splitlines() if 'ASTRA_ANIMAL VERIFIED' in line]
 assert len(lines)==1 and 'reload=false' in lines[0] and 'BUILD SUCCESSFUL' in log and 'ASTRA_ANIMAL FAILED' not in log,'Missing verified animal quest run'
 assert 'ASTRA_ANIMAL the village posted the flock card by itself' in log,'The flock card was not posted by the village itself'
 paths=re.findall(r'ASTRA_ANIMAL screenshot (.+)',log)
 assert len(paths)==3,'Expected camp, freed and pen captures'
 for path in paths:
  frame=GAME_DIR/path.strip()
  assert frame.resolve().is_relative_to(Path(__file__).resolve().parents[1]/'docs/runs') and frame.is_file() and frame.stat().st_size>10000,'Missing actual capture'
elif args.mode=='smithy-courier':
 assert 'ASTRA_SMITHY_COURIER FAILED' not in log and 'BUILD SUCCESSFUL' in log
 assert log.count('ASTRA_SMITHY_COURIER VERIFIED naturalTicks=true loaded=true delivered=1 source=0 cargo=0 walked=true stable=true')==1
 paths=re.findall(r'ASTRA_SMITHY_COURIER screenshot (.+)',log)
 assert len(paths)==1
 frame=GAME_DIR/paths[0].strip()
 assert frame.resolve().is_relative_to(Path(__file__).resolve().parents[1]/'docs/runs') and frame.is_file() and frame.stat().st_size>10000,'Missing actual capture'
elif args.mode=='medicine-reload':
 assert 'ASTRA_MEDICINE_RELOAD FAILED' not in log and 'BUILD SUCCESSFUL' in log
 assert log.count('ASTRA_MEDICINE_RELOAD VERIFIED sick=true stock=0 dose=0 receipt=true samples=10')==1
elif args.mode=='medicine-pickup':
 assert 'ASTRA_MEDICINE_PICKUP FAILED' not in log and 'BUILD SUCCESSFUL' in log
 assert log.count('ASTRA_MEDICINE_PICKUP VERIFIED naturalTicks=true loaded=true cured=true paid=1 noWolf=true walked=true noFreeSecondCure=true')==1
 paths=re.findall(r'ASTRA_MEDICINE_PICKUP screenshot (.+)',log)
 assert len(paths)==1
 frame=GAME_DIR/paths[0].strip()
 assert frame.resolve().is_relative_to(Path(__file__).resolve().parents[1]/'docs/runs') and frame.is_file() and frame.stat().st_size>10000,'Missing actual capture'
elif args.mode=='medicine-wolf':
 assert 'ASTRA_MEDICINE_WOLF FAILED' not in log and 'BUILD SUCCESSFUL' in log
 assert log.count('ASTRA_MEDICINE_WOLF VERIFIED naturalTicks=true loaded=true cured=true paid=1 released=true walked=true noFreeSecondCure=true')==1
 paths=re.findall(r'ASTRA_MEDICINE_WOLF screenshot (.+)',log)
 assert len(paths)==1
 frame=GAME_DIR/paths[0].strip()
 assert frame.resolve().is_relative_to(Path(__file__).resolve().parents[1]/'docs/runs') and frame.is_file() and frame.stat().st_size>10000,'Missing actual capture'
elif args.mode=='smithy-wolf':
 assert 'ASTRA_SMITHY_WOLF FAILED' not in log and 'BUILD SUCCESSFUL' in log
 assert log.count('ASTRA_SMITHY_WOLF VERIFIED armourInteraction=true halfDamage=true wear=63 realDog=true loaded=true delivered=1 source=0 released=true walked=true')==1
 paths=re.findall(r'ASTRA_SMITHY_WOLF screenshot (.+)',log)
 assert len(paths)==1
 frame=GAME_DIR/paths[0].strip()
 assert frame.resolve().is_relative_to(Path(__file__).resolve().parents[1]/'docs/runs') and frame.is_file() and frame.stat().st_size>10000,'Missing actual capture'
elif args.mode=='patrol':
 assert 'ASTRA_PATROL FAILED' not in log and 'BUILD SUCCESSFUL' in log
 assert log.count('ASTRA_PATROL VERIFIED naturalTicks=true walked=true torches=2 paid=2 reserved=true')==1
 paths=re.findall(r'ASTRA_PATROL screenshot (.+)',log)
 assert len(paths)==1
 frame=GAME_DIR/paths[0].strip()
 assert frame.resolve().is_relative_to(Path(__file__).resolve().parents[1]/'docs/runs') and frame.is_file() and frame.stat().st_size>10000,'Missing actual capture'
elif args.mode=='engineering-trap':
 assert log.count('ASTRA_ENGINEERING_TRAP VERIFIED lockedRefund=true survivalPlaced=2 spent=2 monsterDamaged=true animalSafe=true naturalTicks=true')==1
 assert 'ASTRA_ENGINEERING_TRAP FAILED' not in log and 'BUILD SUCCESSFUL' in log
 paths=re.findall(r'ASTRA_ENGINEERING_TRAP screenshot (.+)',log)
 assert len(paths)==1
 for path in paths:
  frame=GAME_DIR/path.strip()
  assert frame.resolve().is_relative_to(Path(__file__).resolve().parents[1]/'docs/runs') and frame.is_file() and frame.stat().st_size>10000
elif args.mode=='lab-desks':
 assert log.count('ASTRA_LAB_DESKS VERIFIED level3=3 level6=6 walked=true distinct=true rotation=1 works=6 clockAdvanced=true')==1
 assert 'ASTRA_LAB_DESKS FAILED' not in log and 'BUILD SUCCESSFUL' in log
 paths=re.findall(r'ASTRA_LAB_DESKS screenshot (.+)',log)
 assert len(paths)==2 and any('-three.png' in p for p in paths) and any('-six.png' in p for p in paths)
 for path in paths:
  frame=GAME_DIR/path.strip()
  assert frame.resolve().is_relative_to(Path(__file__).resolve().parents[1]/'docs/runs') and frame.is_file() and frame.stat().st_size>10000
elif args.mode=='raid-unload':
 lines=[line for line in log.splitlines() if 'ASTRA_RAID_UNLOAD VERIFIED' in line]
 assert len(lines)==1 and 'BUILD SUCCESSFUL' in log and 'ASTRA_RAID_UNLOAD FAILED' not in log
 assert re.search(r'spawned=[1-9]\d* centreActive=true unloaded=true resumed=true identities=true withdrew=true retiredAbsent=true',lines[0])
 paths=re.findall(r'ASTRA_RAID_UNLOAD screenshot (.+)',log)
 assert len(paths)==2 and any('-resumed.png' in p for p in paths) and any('-retired.png' in p for p in paths)
 for path in paths:
  frame=GAME_DIR/path.strip()
  assert frame.resolve().is_relative_to(Path(__file__).resolve().parents[1]/'docs/runs') and frame.is_file() and frame.stat().st_size>10000
elif args.mode=='caravan':
 if args.caravan_checkpoint:
  assert not args.reload and 'BUILD SUCCESSFUL' in log
  assert log.count('ASTRA_CARAVAN_RESTART VERIFIED stage=saved transit=true cargo=true paid=0')==1
  print(json.dumps({'status':'PASSED','mode':'caravan-checkpoint'}));raise SystemExit(0)
 if args.reload:
  assert log.count('ASTRA_CARAVAN_RESTART VERIFIED stage=returned newProcess=true identity=true sourceUnchanged=true reload=true')==1
 if args.caravan_horse:
  horses=[line for line in log.splitlines() if 'ASTRA_CARAVAN_HORSE VERIFIED' in line]
  assert len(horses)==1 and f'workers=0 followed=true delivered=true returned=true parked=1 identity=true reload={str(args.reload).lower()}' in horses[0],'Missing real horse round trip without worker'
  assert 'ASTRA_CARAVAN FAILED' not in log,'Horse caravan failed'
 if args.caravan_dog:
  dogs=[line for line in log.splitlines() if 'ASTRA_CARAVAN_DOG VERIFIED' in line]
  assert len(dogs)==1 and f'carts=2 followed=true delivered=1000 returned=true parked=2 reload={str(args.reload).lower()}' in dogs[0],'Missing real dog cart round trip'
 if args.caravan_escort:
  guards=[line for line in log.splitlines() if 'ASTRA_CARAVAN_ESCORT VERIFIED' in line]
  assert len(guards)==1 and 'recruited=2 followed=true returned=2 reload=false' in guards[0],'Missing real escort round trip'
 lines=[line for line in log.splitlines() if 'ASTRA_CARAVAN VERIFIED' in line]
 assert len(lines)==1 and f'reload={str(args.reload).lower()}' in lines[0] and 'BUILD SUCCESSFUL' in log,'Missing verified caravan delivery'
 assert 'materialized=true' in log,'Caravaneer never materialized'
 paths=re.findall(r'ASTRA_CARAVAN screenshot (.+)',log)
 assert len(paths)>=1,'Expected a delivery capture'
 for path in paths:
  frame=GAME_DIR/path.strip()
  assert frame.resolve().is_relative_to(Path(__file__).resolve().parents[1]/'docs/runs') and frame.is_file() and frame.stat().st_size>10000,'Missing actual capture'
elif args.mode=='road':
 lines=[line for line in log.splitlines() if 'ASTRA_ROAD VERIFIED' in line]
 assert len(lines)==1 and 'reload=false' in lines[0] and 'BUILD SUCCESSFUL' in log,'Missing verified road project'
 paths=re.findall(r'ASTRA_ROAD screenshot (.+)',log)
 assert len(paths)>=2,'Expected palette and road captures'
 for path in paths:
  frame=GAME_DIR/path.strip()
  assert frame.resolve().is_relative_to(Path(__file__).resolve().parents[1]/'docs/runs') and frame.is_file() and frame.stat().st_size>10000,'Missing actual capture'
elif args.mode=='atlas':
 lines=[line for line in log.splitlines() if 'ASTRA_ATLAS VERIFIED' in line]
 assert len(lines)==1 and 'reload=false' in lines[0] and 'BUILD SUCCESSFUL' in log,'Missing verified atlas survey and scene'
 # The scene, and since AD-092 the side section too: the scene is the one this mode checks.
 paths=[p for p in re.findall(r'ASTRA_ATLAS screenshot (.+)',log) if 'atlas-scene' in p]
 assert len(paths)==1,'Expected one scene capture'
 frame=GAME_DIR/paths[0].strip()
 assert frame.resolve().is_relative_to(Path(__file__).resolve().parents[1]/'docs/runs') and frame.is_file() and frame.stat().st_size>10000,'Missing actual capture'
elif args.mode=='trade':
 lines=[line for line in log.splitlines() if 'ASTRA_TRADE VERIFIED' in line]
 assert len(lines)==1 and 'reload=false' in lines[0] and 'BUILD SUCCESSFUL' in log,'Missing verified dialog trade'
 assert log.count('ASTRA_TRADE deal ')==2,'Expected exactly two server-recorded deals'
 paths=re.findall(r'ASTRA_TRADE screenshot (.+)',log)
 assert len(paths)==2,'Expected card and deal captures'
 for path in paths:
  frame=GAME_DIR/path.strip()
  assert frame.resolve().is_relative_to(Path(__file__).resolve().parents[1]/'docs/runs') and frame.is_file() and frame.stat().st_size>10000,'Missing actual capture'
elif args.mode=='building-order':
 lines=[line for line in log.splitlines() if 'ASTRA_BUILD_ORDER VERIFIED' in line]
 assert len(lines)==1 and f"reload={str(args.reload).lower()}" in lines[0] and 'BUILD SUCCESSFUL' in log,'Missing verified field-ordered construction'
 paths=re.findall(r'ASTRA_BUILD_ORDER screenshot (.+)',log)
 assert args.reload or len(paths)==3,'Expected palette, progress and finished house captures'
 for path in paths:
  frame=GAME_DIR/path.strip()
  assert frame.resolve().is_relative_to(Path(__file__).resolve().parents[1]/'docs/runs') and frame.is_file() and frame.stat().st_size>10000,'Missing actual capture'
elif args.mode=='mayor-tool':
 assert log.count('ASTRA_MAYOR VERIFIED')==1 and 'BUILD SUCCESSFUL' in log,'Missing real shovel interaction verification'
 paths=re.findall(r'ASTRA_MAYOR screenshot (.+)',log)
 assert len(paths)==4,'Expected hall, catalogue, world projection and road palette captures'
 for path in paths:
  frame=GAME_DIR/path.strip()
  assert frame.resolve().is_relative_to(Path(__file__).resolve().parents[1]/'docs/runs') and frame.is_file() and frame.stat().st_size>10000,'Missing actual capture'
elif args.mode=='world-interaction':
 assert log.count('ASTRA_WORLD_INTERACTION VERIFIED')==1 and 'BUILD SUCCESSFUL' in log,'Missing verified physical interaction sequence'
 paths=re.findall(r'ASTRA_WORLD_INTERACTION screenshot (.+)',log)
 assert len(paths)==2,'Expected management and world projection captures'
 for path in paths:
  frame=GAME_DIR/path.strip()
  assert frame.resolve().is_relative_to(Path(__file__).resolve().parents[1]/'docs/runs') and frame.is_file() and frame.stat().st_size>10000,'Missing actual capture'
elif args.mode=='cargo-crash':
 assert args.boundary,'Crash boundary is required'
 assert 'ASTRA_CARGO RETURN VERIFIED' in log,'No completed physical return before crash'
 assert f'ASTRA_CRASH_BOUNDARY {args.boundary}' in log and 'exit value 86' in log,'Missing exact intentional process-crash boundary'
elif args.mode in ('office','election','crops','science','logistics','research-graph'):
 prefix='ASTRA_RESEARCH_GRAPH VERIFIED' if args.mode=='research-graph' else 'ASTRA_LOGISTICS VERIFIED' if args.mode=='logistics' else 'ASTRA_SCIENCE VERIFIED' if args.mode=='science' else 'ASTRA_OFFICE VERIFIED' if args.mode=='office' else 'ASTRA_CROPS VERIFIED' if args.mode=='crops' else 'ASTRA_ELECTION VERIFIED'
 lines=[line for line in log.splitlines() if prefix in line]
 assert len(lines)==1 and 'BUILD SUCCESSFUL' in log,'Missing actual office client verification'
 assert f"reload={str(args.reload).lower()}" in lines[0],'Wrong office launch mode'
 if args.mode in ('election','crops','science','logistics','research-graph'):
  marker='ASTRA_RESEARCH_GRAPH' if args.mode=='research-graph' else 'ASTRA_LOGISTICS' if args.mode=='logistics' else 'ASTRA_SCIENCE' if args.mode=='science' else 'ASTRA_ELECTION' if args.mode=='election' else 'ASTRA_CROPS'
  paths=re.findall(marker+r' screenshot (.+)',log)
  assert len(paths)==1,'Missing actual election screenshot'
  frame=GAME_DIR/paths[0].strip()
  assert frame.resolve().is_relative_to(Path(__file__).resolve().parents[1]/'docs/runs') and frame.is_file() and frame.stat().st_size>10000,'Missing election capture'
elif args.mode in ('construction','draft'):
 if args.mode=='draft':assert log.count('ASTRA_DRAFT VERIFIED')==1,'Missing confirmed real draft'
 assert log.count('ASTRA_CONSTRUCTION VERIFIED')==1 and 'BUILD SUCCESSFUL' in log,'Missing actual client verification'
 if args.reload:assert 'reload=true' in log,'Wrong reload mode'
 assert log.count('ASTRA_CONSTRUCTION screenshot')==3,'Missing world/card/progress captures'
 import re
 for path in re.findall(r'ASTRA_CONSTRUCTION screenshot (.+)',log):
  frame=GAME_DIR/path.strip()
  assert frame.resolve().is_relative_to(Path(__file__).resolve().parents[1]/'docs/runs') and frame.is_file() and frame.stat().st_size>10000,'Missing real capture'
else:
 prefix='ASTRA_CARGO VERIFIED' if args.mode=='cargo' else 'ASTRA_FARM VERIFIED'
 lines=[line for line in log.splitlines() if prefix in line]
 assert len(lines)==1,'Missing or ambiguous in-game verification'
 assert f"reload={str(args.reload).lower()}" in lines[0],'Wrong launch mode'
 assert 'BUILD SUCCESSFUL' in log,'No successful completion of Gradle command'
print(json.dumps({'status':'PASSED','mode':args.mode,'reload':args.reload,'boundary':args.boundary,'log':str(args.log),'scope':'Actual server-side assertions in the real Minecraft client; no visual or full-release acceptance claim'},indent=2))
