#dd-cell
import pandas as pd
#dd-cell
# Get all data
rangesAsSet = inputs['rangesAsSet']
ranges = rangesAsSet.set_index(['customer'])

rawData = inputs['rawData']
customers = rawData['customer'].unique().tolist()
priceIndices = rawData['priceIndex'].unique().tolist()
rawData = rawData.set_index(['customer', 'priceIndex'])

weights = inputs['weights']
weights = weights.set_index(['name'])

parameters = inputs['parameters']
parameters = parameters.set_index(['name'])        

volumeBound = parameters.value["minVolume"];
maxAvgPriceIncrease = parameters.value["maxAvgIncrease"];
minRevenue = parameters.value["minRevenue"];

isVolumeCstActive = parameters.value["isVolumeCstActive"];
isAvgPriceIncActive = parameters.value["isAvgPriceIncActive"];
isRevCstActive = parameters.value["isRevCstActive"];

revenueWeight = weights.value["revenue"];
volumeWeight = weights.value["volume"];
avgIncWeight = weights.value["avgInc"];

priceIndiceSubset =  [ pi for pi in priceIndices if priceIndices.index(pi)<len(priceIndices)-1 ]
 
price = rawData.price
tau = rawData.probability
rev = rawData.revenue
 
previousPrice = ranges.previousPrice
#dd-cell
# Do some pre processing

print ("executing PRE_PROCESS");
import math

lambdaPrev = {}
tauPrevLow = {}
tauPrevUpp = {}
pricePrevLow = {}
pricePrevUpp ={}

for c in customers:
    for pi in priceIndiceSubset:
        lowIndex = rawData.loc[(c,pi)];
        uppIndex = rawData.loc[(c,pi+1)];
        if( lowIndex.price <= previousPrice[c] and previousPrice[c] < uppIndex.price):
            delta = price[c][priceIndiceSubset[0]+1]-price[c][priceIndiceSubset[0]];
            k = math.floor(previousPrice[c]/delta);
            e = (k+1)*delta - previousPrice[c];
            lp = e / delta ;
            lambdaPrev[c] = lp;
            tauPrevLow[c] = lowIndex.probability;
            tauPrevUpp[c] = uppIndex.probability;
            pricePrevLow[c] = lowIndex.price;
            pricePrevUpp[c] = uppIndex.price;
#dd-cell
# Create new model

from docplex.mp.model import Model

# Model
mdl = Model(name='InsurancePricing')
#dd-cell
# create variables

lambda1 = mdl.continuous_var_matrix(customers, priceIndices, lb=0, ub=1, name='lambda1')
lambda2 = mdl.continuous_var_matrix(customers, priceIndices, lb=0, ub=1, name='lambda2')
z = mdl.binary_var_matrix(customers, priceIndiceSubset, name='z')
#dd-cell
# Create KPIs and objectives

revenue = mdl.sum(lambda1[c, pi]*rev[c, pi] + lambda2[c, pi]*rev[c, pi+1] for c in customers for pi in priceIndiceSubset)
mdl.add_kpi(revenue, publish_name="KPI.Revenue");
 
volumePerCust = {}
priceApplied = {}
for c in customers:
    volumePerCust[c] = mdl.sum(lambda1[c, pi]*tau[c, pi] + lambda2[c, pi]*tau[c, pi+1] for pi in priceIndiceSubset)
    priceApplied[c] = mdl.sum(lambda1[c, pi]*price[c, pi] + lambda2[c, pi]*price[c, pi+1] for pi in priceIndiceSubset)

volume = mdl.sum(volumePerCust[c] for c in customers)
mdl.add_kpi(volume, publish_name="KPI.Volume")


averagePriceIncrease = mdl.sum(((priceApplied[c]-previousPrice[c])/previousPrice[c])/len(customers) for c in customers)
mdl.add_kpi(averagePriceIncrease, publish_name="KPI.AvgPriceIncrease")
 
resRevenue = - revenueWeight * revenue;
resVolume = - volumeWeight * volume ;
resAvgPriceIncrease = avgIncWeight * averagePriceIncrease;
 
mdl.minimize(resRevenue + resVolume + resAvgPriceIncrease)

mdl.print_information()
#dd-cell
# Create constraints
 
for c in customers:
    for pi in priceIndiceSubset:
        mdl.add_constraint(lambda1[c, pi] + lambda2[c, pi] - z[c, pi] == 0, 'ctConvexityCondition')

for c in customers:
    mdl.add_constraint( mdl.sum(z[c, pi] for pi in priceIndiceSubset) ==1, 'ctSinglePrice')        
    mdl.add_constraint( priceApplied[c] >= ranges.lowerBound[c], 'ctLowerPrice')
    mdl.add_constraint( priceApplied[c] <= ranges.upperBound[c], 'ctUpperPrice')

if isVolumeCstActive == 1:
    mdl.add_constraint( volume>=volumeBound, 'ctVolume' )

if isAvgPriceIncActive == 1:
    mdl.add_constraint( averagePriceIncrease <= maxAvgPriceIncrease, 'ctAveragePriceIncrease')

if isRevCstActive == 1:
    mdl.add_constraint(revenue >= minRevenue, 'ctRevenueMin')
        
mdl.print_information()        
#dd-cell
# solve

ok = mdl.solve()

mdl.print_solution()
#dd-cell
# Some post processing
 
result = [ [c,volumePerCust[c].solution_value,priceApplied[c].solution_value,previousPrice[c], priceApplied[c].solution_value - previousPrice[c]] for c in customers ]
outputs['result'] = pd.DataFrame(data=result, columns=['customer', 'volume', 'price', 'previousPrice', 'delta'])

outputs['objective'] = pd.DataFrame(data=[[revenue.solution_value,volume.solution_value,averagePriceIncrease.solution_value]], columns=['Revenue','Volume','AvgPriceIncrease'])
 
 
