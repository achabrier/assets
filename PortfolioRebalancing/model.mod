
// Input

tuple TCurrencies {
  key string value;
};
{TCurrencies} Currencies = ...;

tuple TDurations {
  key string value;
};
{TDurations} Durations = ...;

tuple TIndustries {
  key string id;
};
{TIndustries} Industries = ...;

/**
 * Global Parameters.
 * - field CashTotal: Initial position Cash in Euro
 * - TransactionCostPercentage: Transaction cost percentage on buy and sell
 * - FixedFee: Fixed fee when buying or selling
 * - UpperLimit/LowerLimit: Upper and Lower limit in percentage for a single company
 * - MaxChanges: maximum number of changes
 */
tuple TParameters {
  key string name;
  float value;
};
{TParameters} Parameters = ...;
{string} ParametersSet = union(p in Parameters) {p.name};
float parameters[ParametersSet] = [ p.name : p.value | p in Parameters];

tuple TSecurityTypes {
  key string type;
};
{TSecurityTypes} SecurityTypes = ...;

tuple TCompanies {
  key string name;
  string industry;
};
{TCompanies} Companies = ...;

tuple TAssets {
  string company_name;
  key string name;
  string type;
  int recomNote;
  string currency;
  string years;
  float price;
};

tuple TDoNotMove {
  string company_name;
  int bool;
};
{TAssets} Assets = ...;
{TDoNotMove} DoNotMove  = ...;



// Shares, Bonds, Cash : USD, EUR, CHF
tuple TPercents {
    string type;
    string currency;
    float value;
}
{TPercents} Percents = ...;
float percents [SecurityTypes][Currencies] = [ <p.type> : [<p.currency> : p.value] | p in Percents];

// Industry percent
tuple TPercentsIndustries {
    key string industry;
    float value;
}
{TPercentsIndustries} PercentsIndustries = ...;
float percentsIndustries[Industries] = [ <p.industry> : p.value | p in PercentsIndustries];

// Exchange rate : USD EUR CHF
tuple TExchangeRates {
    key string currency;
    float value;
}
{TExchangeRates} ExchangeRates = ...;
float exchangeRates[Currencies] = [ <q.currency> : q.value | q in ExchangeRates];

// Cash percent
tuple TPercentsCash {
    key string currency;
    float value;
}
{TPercentsCash} PercentsCash = ...;
float percentsCash[Currencies] = [ <q.currency> : q.value | q in PercentsCash];

tuple TQuantities {
    key string asset;
    int quantity;
}
{TQuantities} InitialQuantities = ...;
int initialQuantities[Assets] = [ <q.asset> : q.quantity | q in InitialQuantities];

// Decision Variables

dvar int deltaQuantity [Assets];
dvar int deltaBuyQuantity [Assets];
dvar int deltaSellQuantity [Assets];
dvar boolean changed [Assets];
dvar float finalCash [Currencies];
dvar boolean ownedCompany [Companies];
dvar int finalQuantities [Assets];

// Helper arrays to get assets of a company or industry
{TAssets} AssetsC[c in Companies] = {};
{TAssets} AssetsI[c in Industries] = {};
execute {
  for (var a in Assets) {
    AssetsC[Companies.find(a.company_name)].add(a);
    AssetsI[Industries.find(Companies.find(a.company_name).industry)].add(a);
  }
}   

// Upper bound for quantities
float maxQuantity = (sum(a in Assets) (a.price * initialQuantities[a] * exchangeRates[<a.currency>]) + parameters["CashTotal"])
/ ((min(a in Assets) a.price) * (min(e in Currencies) exchangeRates[e]));

int M = ftoi(ceil(maxQuantity));

float portfolioValue = sum(a in Assets) (a.price * initialQuantities[a] * exchangeRates[<a.currency>]) + parameters["CashTotal"];

dvar float+ portfolioAssetsValue;

dexpr float Notation = sum(a in Assets) (a.price * finalQuantities[a] * exchangeRates[<a.currency>] * a.recomNote);
dexpr float TransactionFixedCost = 
  sum(a in Assets) changed[a] * parameters["FixedFee"];
dexpr float TransactionVariableCost =
  sum(a in Assets) (deltaBuyQuantity[a] + deltaSellQuantity[a]) * parameters["TransactionCostPercentage"] / 100.;

maximize Notation - TransactionFixedCost - TransactionVariableCost;

subject to {
	
	forall(a in Assets) {
	  -M <= deltaQuantity[a] <= M;
	  0 <= deltaBuyQuantity[a] <= M;
	  0 <= deltaSellQuantity[a] <= M;
	  0 <= finalQuantities[a] <= M;
 	}	  
	
	// quantity of assets from the company c : quantity[c]
	forall(a in Assets)
	   finalQuantities[a] == initialQuantities[a] + deltaBuyQuantity[a] - deltaSellQuantity[a];


	// Owning assets or not : quantity > 0 => owned = 1 ; quantity = 0 => owned = 0
	forall(c in Companies) 
	   ownedCompany[c] == 1 => sum(a in AssetsC[c]) finalQuantities[a] >= 1;
	   
	forall(a in Assets) 
	   ownedCompany[<a.company_name>] == 0 => finalQuantities[a] == 0;
	
	// Companies delta
	forall(a in Assets) 
		deltaQuantity[a] == deltaBuyQuantity[a] - deltaSellQuantity[a];
	
	// cannot sell and buy of the same
	forall(a in Assets)
		(deltaBuyQuantity[a] == 0) || (deltaSellQuantity[a] == 0);
		
	// changed : if sell or buy
	forall(a in Assets)
		deltaBuyQuantity[a] + deltaSellQuantity[a] == 0 => changed[a] == 0; 
	
	forall(a in Assets)
		deltaBuyQuantity[a] + deltaSellQuantity[a] >= 1 => changed[a] == 1;
	
	/**************************************************/
	/******* Don't modify if Move[c] = 0 *****************/
	/**************************************************/
	 
	forall(c in DoNotMove, a in AssetsC[<c.company_name>] : c.bool==0)
	   deltaBuyQuantity[a] == 0 && deltaSellQuantity[a] == 0;
	   
	/**************************************************/
	/******* lower and Upper company % limits *********/
	/**************************************************/
	forall(c in Companies)
	   LowLimitCompanyPercent:
	      parameters["LowerLimit"] * portfolioAssetsValue / 100 
	      <= 
	      sum(a in AssetsC[c])
	      	a.price * finalQuantities[a] * exchangeRates[<a.currency>] + maxint * (1 - ownedCompany[c]);	
	
	forall(c in Companies)
	   UpperLimitCompanyPercent:
	      sum(a in AssetsC[c])
	      	a.price * finalQuantities[a] * exchangeRates[<a.currency>]
	      <= 
	      parameters["UpperLimit"] * portfolioAssetsValue / 100;
	
	
	/**************************************************/
	/********************** Shares Bonds Cash *********/
	/**************************************************/
	
	// Bonds and Shares
	forall(t in SecurityTypes)
	forall(m in Currencies)
	  LowerLimitBySecurityType:
	  portfolioAssetsValue * ((percents[t][m] - parameters["Tolerance"]) / 100.) <=
	  sum (a in Assets : 
	   <a.type> == t && 
	   <a.currency> == m)
	    (a.price * finalQuantities[a] * exchangeRates[<a.currency>]);
	
	forall(t in SecurityTypes)
	forall(m in Currencies) 
	  UpperLimitBySecurityType: 
	  sum (a in Assets : 
	   <a.type> == t && 
	   <a.currency> == m)
	    (a.price * finalQuantities[a] * exchangeRates[<a.currency>])
	    <= portfolioAssetsValue * ((percents[t][m] + parameters["Tolerance"]) / 100.);
	
	// Cash
	forall(m in Currencies)
	  LowerLimitByCurrencyCash:
	  portfolioValue * ((percentsCash[m] - parameters["Tolerance"]) / 100.) <= finalCash[m] * exchangeRates[m];
	  
	forall(m in Currencies)
	  UpperLimitByCurrencyCash:
	  finalCash[m] * exchangeRates[m] <= portfolioValue * ((percentsCash[m] + parameters["Tolerance"]) / 100.);
	
	/**************************************************/
	/**************** Industry **********************/
	/**************************************************/
	
	forall(t in Industries)
	      LowerLimitByIndustry:
	      ((percentsIndustries[t] - parameters["Tolerance"]) / 100.)  * portfolioAssetsValue 
	      <= 
	      sum(a in AssetsI[t]) (a.price * finalQuantities[a] * exchangeRates[<a.currency>]);
	      
	forall(t in Industries)
	      UpperLimitByIndustry:
	      sum(a in AssetsI[t]) (a.price * finalQuantities[a] * exchangeRates[<a.currency>])
	      <= 
	      ((percentsIndustries[t] + parameters["Tolerance"]) / 100.) * portfolioAssetsValue;
	
	/**************************************************/
	/**************** Positions changes ***************/
	/**************************************************/
	sum (a in Assets) changed[a] <= parameters["MaxChanges"]; 
	
	
	portfolioValue == portfolioAssetsValue + sum(m in Currencies) finalCash[m] * exchangeRates[m];
	portfolioAssetsValue == sum(a in Assets) (a.price * finalQuantities[a] * exchangeRates[<a.currency>]);

};

// KPIs for business display

dexpr float portfolioAssetsValueKPI = portfolioAssetsValue;
dexpr float portfolioFitnessKPI = sum(a in Assets) ((a.price * initialQuantities[a] * exchangeRates[<a.currency>] / portfolioValue) * a.recomNote);
dexpr float portfolioTotalValueKPI = portfolioValue;
dexpr float transactionFixedCostKPI = TransactionFixedCost;
dexpr float transactionVariableCostKPI = TransactionVariableCost;
dexpr float cashKPI = sum(m in Currencies) finalCash[m] * exchangeRates[m];
dexpr float numChangesKPI = sum (a in Assets) changed[a];


// Output tables for reporting

tuple TAssetsResults {
  key TAssets asset;
  int quantityBefore;
  int valueBefore;
  int quantityAfter;
  int valueAfter;
};
{TAssetsResults} AssetResults = {<a, 
	initialQuantities[a], 
	ftoi(round(a.price * initialQuantities[a] * exchangeRates[<a.currency>])),
	finalQuantities[a],
	ftoi(round(a.price * finalQuantities[a] * exchangeRates[<a.currency>]))> | a in Assets};
	
	
tuple TCashResults {
    key TCurrencies currency;
    int valueBefore;
    int valueAfter;
}
{TCashResults} CashResults ={<c,
    ftoi(round(parameters["CashTotal"]*percentsCash[c]/ 100.)),
    ftoi(round(finalCash[c]))> | c in Currencies};

tuple TPortfolio {
  key string asset; 
  string company_name;
  string industry;
  string type;
  int recomNote;
  string currency;
  string years;
  int amount;
  //float amount;
}

{TPortfolio} InitialPortfolio = {  
    <a.name,a.company_name,c.industry,a.type,a.recomNote,a.currency,a.years,ftoi(round(a.price*initialQuantities[a]))>
    //<a.name,a.company_name,c.industry,a.type,a.recomNote,a.currency,a.years,a.price*initialQuantities[a]>
    | a in Assets, c in Companies : c.name == a.company_name && initialQuantities[a] > 0
};

{TPortfolio} FinalPortfolio = {  
    <a.name,a.company_name,c.industry,a.type,a.recomNote,a.currency,a.years,ftoi(round(a.price*finalQuantities[a]))>
    //<a.name,a.company_name,c.industry,a.type,a.recomNote,a.currency,a.years,a.price*finalQuantities[a]>
    | a in Assets, c in Companies : c.name == a.company_name && finalQuantities[a] > 0
};

{TQuantities} FinalQuantities = {
    <a.name, finalQuantities[a]>
    | a in Assets
};