from docplex.util.environment import get_environment
from os.path import splitext
import pandas
from six import iteritems

def get_all_inputs():
    '''Utility method to read a list of files and return a tuple with all
    read data frames.
    Returns:
        a map { datasetname: data frame }
    '''
    result = {}
    env = get_environment()
    for iname in [f for f in os.listdir('.') if splitext(f)[1] == '.csv']:
        with env.get_input_stream(iname) as in_stream:
            df = pandas.read_csv(in_stream)
            datasetname, _ = splitext(iname)
            result[datasetname] = df
    return result

def write_all_outputs(outputs):
    '''Write all dataframes in ``outputs`` as .csv.

    Args:
        outputs: The map of outputs 'outputname' -> 'output df'
    '''
    for (name, df) in iteritems(outputs):
        csv_file = '%s.csv' % name
        print(csv_file)
        with get_environment().get_output_stream(csv_file) as fp:
            if sys.version_info[0] < 3:
                fp.write(df.to_csv(index=False, encoding='utf8'))
            else:
                fp.write(df.to_csv(index=False).encode(encoding='utf8'))
    if len(outputs) == 0:
        print("Warning: no outputs written")
 
import os
print (os.environ)

# Load CSV files into inputs dictionnary
inputs = get_all_inputs()

#dd-cell
food = inputs['diet_food']
nutrients = inputs['diet_nutrients']
food_nutrients = inputs['diet_food_nutrients']
food_nutrients.set_index('Food', inplace=True)
#dd-cell
from docplex.mp.model import Model
from docplex.mp.environment import Environment
Environment().print_information()

# Model
mdl = Model(name='diet')

# Create decision variables, limited to be >= Food.qmin and <= Food.qmax
qty = food[['name', 'qmin', 'qmax']].copy()
qty['var'] = qty.apply(lambda x: mdl.continuous_var(lb=x['qmin'],
                                                    ub=x['qmax'],
                                                    name=x['name']),
                       axis=1)
# make the name the index
qty.set_index('name', inplace=True)

# Limit range of nutrients, and mark them as KPIs
for n in nutrients.itertuples():
    amount = mdl.sum(qty.loc[f.name]['var'] * food_nutrients.loc[f.name][n.name]
                     for f in food.itertuples())
    mdl.add_range(n.qmin, amount, n.qmax)
    mdl.add_kpi(amount, publish_name='Total %s' % n.name)

# Minimize cost
obj = mdl.sum(qty.loc[f.name]['var'] * f.unit_cost for f in food.itertuples())
mdl.add_kpi(obj, publish_name="Minimal cost");
mdl.minimize(obj)

mdl.print_information()
#dd-markdown <h1>Solve</h1>
#dd-cell
ok = mdl.solve(log_output=True)
#dd-cell
mdl.print_solution()
#dd-markdown Make dataframe from solution
#dd-cell
import pandas
import numpy

solution_df = pandas.DataFrame(columns=['name', 'value'])

for index, dvar in enumerate(mdl.solution.iter_variables()):
    solution_df.loc[index,'name'] = dvar.to_string()
    solution_df.loc[index,'value'] = dvar.solution_value
#dd-cell
solution_df
#dd-cell
outputs = {}
outputs['solution'] = solution_df

# Generate output files
write_all_outputs(outputs)
