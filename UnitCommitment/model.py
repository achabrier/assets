


from docplex.mp.environment import Environment
from docplex.mp.model import Model
import pandas as pd

scenario = "Baseline"

df_units = inputs['Units']
df_units.rename(columns={scenario: "value"}, inplace=True)
df_units['value'] = df_units['value'].fillna(0)
units = df_units['Units'].unique().tolist()
df_units.set_index(["Units", "UnitProperties"], inplace=True)
print(df_units.head())

df_loads = inputs['Loads']
df_loads.rename(columns={scenario: "value"}, inplace=True)
df_loads['value'] = df_loads['value'].fillna(0)
periods = df_loads["Periods"].unique().tolist()
firstPeriod = periods[0]
lastPeriod = periods[-1]
nextPeriod = {}
nbPeriods = len(periods)
for i in range(len(periods)-1):
    nextPeriod[periods[i]] = periods[i+1]
df_loads.set_index(["Periods"], inplace=True)
print(df_loads.head())

df_exchanges = inputs['Exchanges']
df_exchanges['Max'] = df_exchanges['Max'].fillna(0)
df_exchanges['Price'] = df_exchanges['Price'].fillna(0)
df_exchanges.set_index(["Periods"], inplace=True)
print(df_exchanges.head())

df_maint = inputs['UnitMaintenances']
df_maint.rename(columns={scenario: "value"}, inplace=True)
df_maint['value'] = df_maint['value'].fillna(0)
df_maint.set_index(["Units", "Periods"], inplace=True)

wtotal_fixed_cost = 1
wtotal_variable_cost = 1
wtotal_startup_cost = 1
wtotal_co2_cost = 1
wtotal_exchange_cost = 1

if ('Weights' in inputs):
    df_weights = inputs['Weights']
    df_weights.rename(columns={scenario: "value"}, inplace=True)
    df_weights['value'] = df_weights['value'].fillna(0)
    df_weights.set_index(df_weights["Weights"], inplace=True)
    if "fixed_cost" in df_weights.index:
        wtotal_fixed_cost = df_weights.value['fixed_cost']
    if "variable_cost" in df_weights.index:
        wtotal_variable_cost = df_weights.value['variable_cost']
    if "startup_cost" in df_weights.index:
        wtotal_startup_cost = df_weights.value['startup_cost']
    if "co2_cost" in df_weights.index:
        wtotal_co2_cost = df_weights.value['co2_cost']
    if "exchange_cost" in df_weights.index:
        wtotal_exchange_cost = df_weights.value['exchange_cost']

robust = 0

env = Environment()
# env.print_information()

ucpm = Model("ucp")
n_starts = None

# in use[u,t] is true iff unit u is in production at period t
in_use = ucpm.binary_var_matrix(keys1=units, keys2=periods, name="in_use")

# true if unit u is turned on at period t
turn_on = ucpm.binary_var_matrix(keys1=units, keys2=periods, name="turn_on")

# true if unit u is switched off at period t
turn_off = ucpm.binary_var_matrix(keys1=units, keys2=periods, name="turn_off")

# production of energy for unit u at period t
production = ucpm.continuous_var_matrix(keys1=units, keys2=periods, name="production")

# exchange of energy at period t
exchange = ucpm.continuous_var_dict(keys=periods, name="exchange")

# When in use, the production level is constrained to be between min and max generation.
ucpm.add_constraints( production[u,p] <= df_units.value[u,"max_generation"] * in_use[u,p] for u in units for p in periods)
ucpm.add_constraints( production[u,p] >= df_units.value[u,"min_generation"] * in_use[u,p] for u in units for p in periods)

# Initial state
# If initial production is nonzero, then period #1 is not a turn_on
# else turn_on equals in_use
# Dual logic is implemented for turn_off
for u in units:
    if (u,"init_prod_level") in df_units.index and  df_units.value[u,"init_prod_level"] > 0:
        # if u is already running, not starting up
        ucpm.add_constraint(turn_on[u, firstPeriod] == 0)
        # turnoff iff not in use
        ucpm.add_constraint(turn_off[u, firstPeriod] + in_use[u, firstPeriod] == 1)
    else:
        # turn on at 1 iff in use at 1
        ucpm.add_constraint(turn_on[u, firstPeriod] == in_use[u, firstPeriod])
        # already off, not switched off at t==1
        ucpm.add_constraint(turn_off[u, firstPeriod] == 0)

# ramp up and down
for unit in units:
    u_ramp_up = df_units.value[unit,"ramp_up"]
    u_ramp_down = df_units.value[unit,"ramp_down"]
    u_initial = df_units.value[unit,"init_prod_level"] if (unit,"init_prod_level") in df_units.index else 0
    # Initial ramp up/down
    # Note that r.production is a Series that can be indexed as an array (ie: first item index = 0)
    ucpm.add_constraint(production[unit, firstPeriod] - u_initial <= u_ramp_up)
    ucpm.add_constraint(u_initial - production[unit, firstPeriod] <= u_ramp_down)
    for p in periods:
        if p is not lastPeriod:
            ucpm.add_constraint(production[unit, nextPeriod[p]] - production[unit, p] <= u_ramp_up)
            ucpm.add_constraint(production[unit, p] - production[unit, nextPeriod[p]] <= u_ramp_down)

# Turn_on, turn_off
for u in units:
    for p in periods:
        if p is not lastPeriod:
            # if unit is off at time t and on at time t+1, then it was turned on at time t+1
            ucpm.add_constraint(in_use[u, nextPeriod[p]] - in_use[u, p] <= turn_on[u, nextPeriod[p]])

            # if unit is on at time t and time t+1, then it was not turned on at time t+1
            # was commented
            ucpm.add_constraint(in_use[u, nextPeriod[p]] + in_use[u, p] + turn_on[u, nextPeriod[p]] <= 2)

            # if unit is on at time t and off at time t+1, then it was turned off at time t+1
            ucpm.add_constraint(in_use[u, p] - in_use[u, nextPeriod[p]] + turn_on[u, nextPeriod[p]] == turn_off[u, nextPeriod[p]])

# Minimum uptime, downtime
for unit in units:
    min_uptime = int(df_units.value[unit,"min_up"])
    min_downtime = int(df_units.value[unit,"min_down"])
    # Note that r.turn_on and r.in_use are Series that can be indexed as arrays (ie: first item index = 0)
    for t in range(min_uptime, nbPeriods):
        ctname = "min_up_" + unit + "_" + periods[t]
        ucpm.add_constraint(ucpm.sum(turn_on[unit, periods[t2]] for t2 in range(t - min_uptime + 1,t + 1)) <= in_use[unit, periods[t]], ctname)

    for t in range(min_downtime, nbPeriods):
        ctname = "min_down_" + unit + "_" + periods[t]
        ucpm.add_constraint(ucpm.sum(turn_off[unit, periods[t2]] for t2 in range((t - min_downtime) + 1, t + 1)) <= 1 - in_use[unit, periods[t]], ctname)

# Enforcing demand
# we use a >= here to be more robust,
# objective will ensure  we produce efficiently
for p in periods:
    total_demand = df_loads.value[p]
    ctname = "ct_meet_demand_" + p
    ucpm.add_constraint(ucpm.sum(production[u,p] for u in units) + exchange[p] >= total_demand + robust, ctname)

# Maximum exchange
for p in periods:
    max_exchange = df_exchanges.Max[p] if (p) in df_exchanges.index else 0
    ctname = "ct_max_exchange_" + p
    ucpm.add_constraint(exchange[p] <= max_exchange, ctname)

# Predefined usage
if 'used' in inputs:
    df_used = inputs['used']
    for p in periods:
        for u in units:
            ucpm.add_constraint(in_use[u, p] == df_used[u][p])

# UnitMaintenances
for p in periods:
    for u in units:
        if (u, p) in df_maint.index and df_maint.value[u, p] == 1 :
            ucpm.add_constraint(in_use[u, p] == 0)

# objective
total_fixed_cost = ucpm.sum(in_use[u,p] * df_units.value[u,"constant_cost"] for u in units for p in periods)
total_variable_cost = ucpm.sum(production[u,p] * df_units.value[u,"linear_cost"] for u in units for p in periods)
total_startup_cost = ucpm.sum(turn_on[u,p] * df_units.value[u,"start_up_cost"] for u in units for p in periods)
total_co2_cost = ucpm.sum(production[u,p] * df_units.value[u,"co2_cost"] for u in units for p in periods)
total_exchange_cost = ucpm.sum(exchange[p] * (df_exchanges.Price[p] if (p) in df_exchanges.index else 0) for p in periods)

total_economic_cost = total_fixed_cost + total_variable_cost + total_startup_cost + total_exchange_cost

total_cost = total_economic_cost + total_co2_cost

total_nb_used = ucpm.sum(in_use[u,p] for u in units for p in periods)
total_nb_starts = ucpm.sum(turn_on[u,p] for u in units for p in periods)

if (n_starts is not None):
    ucpm.add_constraint(total_nb_starts == n_starts)

# store expression kpis to retrieve them later.
ucpm.add_kpi(total_fixed_cost, "Total Fixed Cost")
ucpm.add_kpi(total_variable_cost, "Total Variable Cost")
ucpm.add_kpi(total_startup_cost, "Total Startup Cost")
# ucpm.add_kpi(total_economic_cost, "Total Economic Cost")
ucpm.add_kpi(total_co2_cost, "Total CO2 Cost")
ucpm.add_kpi(total_exchange_cost, "Total Exchange Cost")
# ucpm.add_kpi(total_cost         , "Total Cost")
# ucpm.add_kpi(total_nb_used, "Total #used")
# ucpm.add_kpi(total_nb_starts, "Total #starts")

# minimize sum of all costs
ucpm.minimize(
    wtotal_fixed_cost * total_fixed_cost + wtotal_variable_cost * total_variable_cost + wtotal_startup_cost * total_startup_cost + wtotal_co2_cost * total_co2_cost + wtotal_exchange_cost * total_exchange_cost)

if ucpm.solve(log_output=True):
    print ("  Feasible " + str(ucpm.objective_value))

    all_kpis = [(kp.name, kp.compute()) for kp in ucpm.iter_kpis()]
    # all_kpis.append(("Feasibility", 1))
    df_kpis = pd.DataFrame(all_kpis, columns=['kpi', scenario])

    df_exchanged = pd.DataFrame(columns=['Periods', scenario],
                                 data=[[p, exchange[p].solution_value] for p in periods])
    df_production = pd.DataFrame(columns=['Units', 'Periods', scenario], data=[[u,p,production[u,p].solution_value] for u in units for p in periods])
    df_used = pd.DataFrame(columns=['Units', 'Periods', scenario], data=[[u,p,in_use[u,p].solution_value] for u in units for p in periods])
    df_started = pd.DataFrame(columns=['Units', 'Periods', scenario],
                           data=[[u, p, turn_on[u, p].solution_value] for u in units for p in periods])

    outputs = {}
    outputs['production'] = df_production
    outputs['exchanged'] = df_exchanged
    print (df_exchanged)
    # outputs['prods'] = df_prods
    outputs['used'] = df_used
    outputs['started'] = df_started
    outputs['kpi'] = df_kpis
    print(df_kpis)
else:
    # print "  Infeasible"
    all_kpis = [("Feasibility", 0)]
    df_kpis = pd.DataFrame(all_kpis, columns=['kpi', scenario])
    outputs = {}
    outputs['kpi'] = df_kpis

