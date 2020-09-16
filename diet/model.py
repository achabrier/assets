
df_food = inputs['diet_food']
foods = df_food['name'].values.tolist()
df_food.set_index('name', inplace=True)

df_nutrients = inputs['diet_nutrients']
nutrients = df_nutrients['name'].values.tolist()
df_nutrients.set_index('name', inplace=True)

df_food_nutrients = inputs['diet_food_nutrients']
df_food_nutrients.set_index('Food', inplace=True)

from docplex.mp.model import Model

# Model
mdl = Model(name='diet')

# Create variables
qty = mdl.continuous_var_dict(foods, name='qty')

# Limit range of foods
for f in foods:
    mdl.add_range(df_food.qmin[f], qty[f], df_food.qmax[f])

# Limit range of nutrients, and mark them as KPIs
for n in nutrients:
    amount = mdl.sum(qty[f] * df_food_nutrients.loc[f][n] for f in foods)
    mdl.add_range(df_nutrients.qmin[n], amount, df_nutrients.qmax[n])
    mdl.add_kpi(amount, publish_name='Total %s' % n)

# Minimize cost
obj = mdl.sum(qty[f] * df_food.unit_cost[f] for f in foods)
mdl.add_kpi(obj, publish_name="Minimal cost");
mdl.minimize(obj)

mdl.print_information()

# solve
ok = mdl.solve()

mdl.print_solution()

import pandas
import numpy

solution_df = pandas.DataFrame(columns=['Food', 'value'])

for index, dvar in enumerate(mdl.solution.iter_variables()):
    solution_df.loc[index, 'Food'] = dvar.to_string()
    solution_df.loc[index, 'value'] = dvar.solution_value

outputs = {}
outputs['solution'] = solution_df
