from docplex.mp.model_reader import ModelReader

mr = ModelReader() 
model = mr.read('diet1.lp')

model.get_cplex().MIP_starts.read("start.mst")

model.print_information()

sol = model.solve(log_output=True)

import pandas as pd
outputs = {}

print("status:", model.solve_details.status)                                    
if sol:                                                                         
    print("obj val:", sol.get_objective_value())
    data = []
    for var,value in sol.iter_var_values():
        data.append([var,value])
    df_sol = pd.DataFrame(data=data, columns=['var', 'value'])
    outputs['solution'] = df_sol
   