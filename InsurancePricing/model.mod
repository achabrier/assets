

range booleanValues = 0..1;

// Input

tuple TParameter {
  key string name;
  float value;
};

tuple TWeight {
  key string name;
  float value;
};

tuple TRange {
  key int customer;
  float lowerBound;
  float upperBound;
  float previousPrice;
};

tuple TRawData {
  int index;
  key int customer;
  key int priceIndex;
  float price;
  float probability;
  float revenue;
};
{TParameter} parameters = ...;
{TWeight} weights = ...;

{TRange} rangesAsSet = ...;
{TRawData} rawData = ...;


float volumeBound = item(parameters, <"minVolume">).value;
float maxAvgPriceIncrease = item(parameters, <"maxAvgIncrease">).value;
float minRevenue = item(parameters, <"minRevenue">).value;

int isVolumeCstActive = ftoi(item(parameters, <"isVolumeCstActive">).value);
int isAvgPriceIncActive = ftoi(item(parameters, <"isAvgPriceIncActive">).value);
int isRevCstActive = ftoi(item(parameters, <"isRevCstActive">).value);

int isPreviousPriceApplied = ftoi(item(parameters, <"isPreviousPriceApplied">).value);

float revenueWeight = item(weights, <"revenue">).value;
float volumeWeight = item(weights, <"volume">).value;
float avgIncWeight = item(weights, <"avgInc">).value;

{int} customers = { raw.customer | raw in rawData};
{int} priceIndices =  { raw.priceIndex | raw in rawData};
{int} priceIndiceSubset =  { pi | pi in priceIndices : ord(priceIndices,pi)<card(priceIndices)-1};
TRange ranges[c in customers]= item(rangesAsSet,c); 
 
float price[c in customers][pi in priceIndices] = item(rawData,<c,pi>).price;
float tau[c in customers][pi in priceIndices] = item(rawData,<c,pi>).probability;
float rev[c in customers][pi in priceIndices] = item(rawData,<c,pi>).revenue;
 
float previousPrice[c in customers] = ranges[c].previousPrice;
float lambdaPrev[c in customers];
float tauPrevLow[c in customers];
float tauPrevUpp[c in customers];
float pricePrevLow[c in customers];
float pricePrevUpp[c in customers];
 
execute PRE_DISPLAY{
  writeln("input data parsed")  
}
execute PRE_PROCESS{
 	writeln("executing PRE_PROCESS");
	for(var c in customers){
		for(var pi in priceIndiceSubset){
			var lowIndex = 	rawData.find(c,pi);
			var uppIndex = 	rawData.find(c,pi+1);			
			if( lowIndex.price <= previousPrice[c] && previousPrice[c] < uppIndex.price){
					var delta = price[c][Opl.first(priceIndiceSubset)+1]-price[c][Opl.first(priceIndiceSubset)];
					var k = Opl.floor(previousPrice[c]/delta);
					var e = (k+1)*delta - previousPrice[c];
					var lp = e / delta ;
					lambdaPrev[c] = lp;
					tauPrevLow[c] = lowIndex.probability;
					tauPrevUpp[c] = uppIndex.probability;	
					pricePrevLow[c] = lowIndex.price;
					pricePrevUpp[c] = uppIndex.price;	
			}		
		}
	}	
 }
 
dvar  float lambda1[c in customers][pi in priceIndices] in 0..1;
dvar  float lambda2[c in customers][pi in priceIndices] in 0..1;
dvar  boolean z[c in customers][pi in priceIndiceSubset];
 
dexpr float revenue = sum(c in customers, pi in priceIndiceSubset)(lambda1[c][pi]*rev[c][pi] + lambda2[c][pi]*rev[c][pi+1]);
dexpr float volumePerCust[c in customers] = sum(pi in priceIndiceSubset)(lambda1[c][pi]*tau[c][pi] + lambda2[c][pi]*tau[c][pi+1]);
dexpr float volume = sum(c in customers)(volumePerCust[c]);
dexpr float priceApplied[c in customers] = sum(pi in priceIndiceSubset)(lambda1[c][pi]*price[c][pi] + lambda2[c][pi]*price[c][pi+1]);
dexpr float averagePriceIncrease = sum(c in customers)((priceApplied[c]-previousPrice[c])/previousPrice[c])/card(customers);

dexpr float revenueIfPrevPriceAppliedPerCust[c in customers] = lambdaPrev[c] * pricePrevLow[c] * tauPrevLow[c] +
 		 (1-lambdaPrev[c])*pricePrevUpp[c] * tauPrevUpp[c];
dexpr float revenueIfPrevPriceApplied = sum(c in customers)(revenueIfPrevPriceAppliedPerCust[c]);
dexpr float volumeIfPrevPriceAppliedPerCust[c in customers] = lambdaPrev[c] * tauPrevLow[c] + (1-lambdaPrev[c])*tauPrevUpp[c];
dexpr float volumeIfPrevPriceApplied = sum(c in customers)(volumeIfPrevPriceAppliedPerCust[c]);
dexpr float previousRevenue = sum(c in customers)(previousPrice[c]);

 
dexpr float resRevenue = - revenueWeight * revenue;
dexpr float resVolume = - volumeWeight * volume ;
dexpr float resAvgPriceIncrease = avgIncWeight * averagePriceIncrease;
 
minimize resRevenue + resVolume + resAvgPriceIncrease;
 
subject to{
	if (isPreviousPriceApplied == 0){
     	forall( c in customers, pi in priceIndiceSubset){
            ctConvexityCondition:
     	 	    lambda1[c][pi] + lambda2[c][pi] - z[c][pi] == 0;
     	}
     	forall(c in customers){
     		ctSinglePrice:
     		    sum(pi in priceIndiceSubset)(z[c][pi]) == 1;
     	}
     	forall(c in customers){
     		ctLowerPrice:
     		    priceApplied[c] >= ranges[c].lowerBound;
     		ctUpperPrice:
     		    priceApplied[c] <= ranges[c].upperBound;
     	}
     
    	if(isVolumeCstActive == 1){
    		ctVolume:
     		    volume>=volumeBound;
    	}
    
    	if(isAvgPriceIncActive == 1){
    		ctAveragePriceIncrease:				
    		    averagePriceIncrease <= maxAvgPriceIncrease;	
    	}
    	
    	if(isRevCstActive == 1){
    		ctRevenueMin:				
    		    revenue >= minRevenue;	
    	}
	}
 }
 
 
 dexpr float Revenue = revenue;
 dexpr float Volume = volume ;
 dexpr float AvgPriceIncrease = averagePriceIncrease;
 
// Output


tuple TResult {
  key int customer;
  float volume;
  float price;
  float previousPrice;
  float delta;
};

{TResult} result;




execute POPULATE_RESULTS{
    var delta = 0;
 	writeln("POPULATE_RESULTS"); 
    if(isPreviousPriceApplied == 1){
		for(var c in customers){
			result.add(c,volumeIfPrevPriceAppliedPerCust[c],previousPrice[c],previousPrice[c],0);
		}
    } 	 	 	 
 	else{
		for(var c in customers){
			delta = priceApplied[c] - previousPrice[c];
			result.add(c,volumePerCust[c],priceApplied[c],previousPrice[c],delta);
		}
 	 }
}
 

tuple TObjective {
  float Revenue;
  float Volume;
  float AvgPriceIncrease;
};

{TObjective} objective;

execute POPULATE_SOLUTION {
    objective.add(Revenue,Volume,AvgPriceIncrease);
}
 
