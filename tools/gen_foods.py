#!/usr/bin/env python3
"""Генерирует app/src/main/assets/foods.csv из USDA FoodData Central SR Legacy (2018-04).
Данные: https://fdc.nal.usda.gov/download-datasets (SR Legacy CSV, public domain / CC0).
Нутриенты: 1008 Energy kcal, 1003 Protein, 1004 Total lipid, 1005 Carbohydrate by difference, 1079 Fiber total dietary.
Углеводы в приложении = «усвояемые» = 1005 − 1079 (как на российских этикетках)."""
import csv, sys, os, json
SR = sys.argv[1]
root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
lines=[l.rstrip('\n') for l in open(os.path.join(root,'tools/foods_map.txt'),encoding='utf-8') if l.strip() and not l.startswith('#')]
mp=[l.split('|') for l in lines if not l.startswith('R|')]
recipes=[l.split('|')[1:] for l in lines if l.startswith('R|')]
ids = {m[0] for m in mp}
desc = {}
for r in csv.DictReader(open(os.path.join(SR,'food.csv'),encoding='utf-8')):
    if r['fdc_id'] in ids: desc[r['fdc_id']] = r['description']
want = {'1008':'kcal','1003':'p','1004':'f','1005':'c','1079':'fib'}
nut = {}
for r in csv.DictReader(open(os.path.join(SR,'food_nutrient.csv'),encoding='utf-8')):
    if r['fdc_id'] in ids and r['nutrient_id'] in want:
        nut.setdefault(r['fdc_id'],{})[want[r['nutrient_id']]] = float(r['amount'])
out = []; bad = []
seen=set()
for fid,name,cat,tags in mp:
    if fid in seen: bad.append((fid,'dup')); continue
    seen.add(fid)
    if fid not in desc: bad.append((fid,name,'missing')); continue
    n = nut.get(fid,{})
    if 'kcal' not in n or 'p' not in n or 'f' not in n or 'c' not in n: bad.append((fid,name,'nutrients',n)); continue
    fib = n.get('fib', 0.0)
    avail = max(0.0, n['c'] - fib)
    out.append([fid,name,cat,f"{n['kcal']:.0f}",f"{n['p']:.2f}",f"{n['f']:.2f}",f"{avail:.2f}",f"{fib:.2f}",tags,desc[fid], '1' if 'fib' in n else '0'])
byid={o[0]:o for o in out}
for i,(name,cat,tags,yld,ings) in enumerate(recipes):
    tot=[0.0]*5; grams=0.0; parts=[]
    for it in ings.split(','):
        fid,g=it.split(':'); g=float(g); o=byid[fid]; grams+=g
        for k in range(5): tot[k]+=float(o[3+k])*g/100.0
        parts.append(f"{o[1]} {g:.0f} г")
    cooked=grams*float(yld)
    v=[t*100.0/cooked for t in tot]
    out.append([f"R{i+1}",name,cat,f"{v[0]:.0f}",f"{v[1]:.2f}",f"{v[2]:.2f}",f"{v[3]:.2f}",f"{v[4]:.2f}",tags,"Recipe: "+"; ".join(parts)+(f"; yield {yld}" if yld!='1.0' else ''),'1'])
with open(os.path.join(root,'app/src/main/assets/foods.csv'),'w',encoding='utf-8',newline='') as f:
    w = csv.writer(f, delimiter=';')
    w.writerow(['fdc_id','name','category','kcal','protein','fat','carbs_available','fiber','tags','usda_description','fiber_reported'])
    w.writerows(out)
print(len(out),'written'); print('problems:',bad)
