/*
 * Licensed Materials - Property of IBM
 * 5724-Y04
 * Copyright IBM Corporation 2005, 2010. All Rights Reserved.
 *
 * Note to U.S. Government Users Restricted Rights:
 * Use, duplication or disclosure restricted by GSA ADP Schedule
 * Contract with IBM Corp.
 */

include "UnitCommitment_odm.mod";

range PeriodRange = parameters.one..parameters.nbPeriods;

dvar float+ Production[units][PeriodRange];
dvar boolean InUse[units][PeriodRange];
dvar boolean TurnOn[units][PeriodRange];
dvar boolean TurnOff[units][PeriodRange];

dexpr float FuelCost = sum(u in units, t in PeriodRange) 
                          (u.constantCost*InUse[u][t] + 
                           u.linearCost*Production[u][t]);
                        
dexpr float StartUpCost = sum(u in units, t in PeriodRange)
                              u.startUpCost*TurnOn[u][t];

dexpr float EcologicalCost = sum(u in units, t in PeriodRange)
                              u.co2Cost*Production[u][t];

minimize FuelCost + StartUpCost + EcologicalCost;

subject to {
   /***  Hard Constraints  ***/
   forall(u in units: u.initProdLevel > 0) {
      // if unit u is already on when this scheduling horizon starts then it was not turned on at the start
      TurnOn[u][1] == 0;
      
      // if unit u is already on when this scheduling horizon starts then it will either be turned off at time 1 or remain on at time 1
      TurnOff[u][1] + InUse[u][1] == 1;
   }
   
   forall(u in units: u.initProdLevel == 0) {
      // if unit u is off when this scheduling horizon starts then it was not turned off at the start
      TurnOff[u][1] == 0;
      
      // if unit u is off when this scheduling horizon starts then the turn on variable must be the same as the is on variable
      TurnOn[u][1] == InUse[u][1];
   }
   
   forall(u in units) {
      forall(t in 1..parameters.nbPeriods-1) {
         //if machine u is off at time t and on at time t+1, then it was turned on at time t+1
         InUse[u][t+1] - InUse[u][t] <= TurnOn[u][t+1];
         
         //Define turnOff variables
         TurnOff[u][t+1] == TurnOn[u][t+1] + InUse[u][t] - InUse[u][t+1];
      }     
   }
   
   /***  Relaxable Constraints  ***/
   forall(l in loads)
      //load requirement (demand must be satisfied)
      meet_demand: sum(u in units) Production[u][l.period.id] == l.load;
      
   forall(u in units, t in PeriodRange) {
      //minimum generation level
      min_generation: Production[u][t] >= InUse[u][t]*u.minGeneration;
      
      //operating maximum level
      oper_max_generation: Production[u][t] <= InUse[u][t]*u.operatingMaxGen;
      
      //absolute maximum level
      max_generation: Production[u][t] <= InUse[u][t]*u.maxGeneration;
   }
   
   forall(u in units) {
      //initial ramp up/down constraints
      init_ramp_up: Production[u][1] - u.initProdLevel <= u.rampUp;
      init_ramp_down: u.initProdLevel - Production[u][1] <= u.rampDown;
      
      //ramp up/down constraints
      forall(t in 1..parameters.nbPeriods-1) {
         ramp_up: Production[u][t+1] - Production[u][t] <= u.rampUp;
         ramp_down: Production[u][t] - Production[u][t+1] <= u.rampDown;
      }
   }
   
   forall(u in units, t in PeriodRange: t > u.minUp)
      //minimum up time
      min_up: sum(i in t-u.minUp+1..t) TurnOn[u][i] <= InUse[u][t];
      
   forall(u in units, t in PeriodRange: t > u.minDown)
      //minimum down time
      min_down: sum(i in t-u.minDown+1..t) TurnOff[u][i] <= 1-InUse[u][t];
      
   
   /***  Rules  ***/
   
   //Maintenance Rules
   forall(r in maintenanceRules)
      maintenance_rule: sum(t in r.period1..r.period2) 
                        InUse[r.unit][t] <= r.period2-r.period1-r.periods+1;              
   
   //Maximum usage per day rules
   forall(r in maxProdRules, i in PeriodRange)
      max_prod_rule: sum(t in i..i+r.periodLength-1: t <= parameters.nbPeriods) 
                     InUse[r.unit][t] <= r.periods;
                     
   //Must run
   forall(r in mustRunRules)
      must_run_rule: sum(t in r.period1..r.period2) 
                     InUse[r.unit][t] == r.period2-r.period1+1;
   
   //Must turn off
   forall(r in mustTurnOffRules)
      must_turn_off_rule: sum(t in r.period1..r.period2) 
                          InUse[r.unit][t] == 0;
                          
   //Spinning reserve requirement
   forall(l in loads)
      reserve_rule: sum(u in units) (u.maxGeneration*InUse[u][l.period.id]-Production[u][l.period.id]) 
      					>= (parameters.reservePercent/100.0)*l.load;
    

   forall(frozen in frozenInUse)
      frozen_rule: InUse[frozen.unit][frozen.period]==frozen.boolValue;
}

int Status[u in units][t in PeriodRange] = TurnOn[u][t] - TurnOff[u][t];
string StatusString[u in units][t in PeriodRange] = statusCodes[Status[u][t]];

//KPIs
float SpinningReserve[t in PeriodRange] = sum(u in units) (u.maxGeneration*InUse[u][t]-Production[u][t]);
int NbPlantsOnline[t in PeriodRange] = sum(u in units) InUse[u][t];
float UtilizationOperating[u in units] = sum(t in PeriodRange) Production[u][t]/(parameters.nbPeriods*u.operatingMaxGen);
float UtilizationPhysical[u in units] = sum(t in PeriodRange) Production[u][t]/(parameters.nbPeriods*u.maxGeneration);
float AvgCostByPeriod[t in PeriodRange] =((sum(u in units) Production[u][t])!=0)? (sum(u in units) (u.constantCost*InUse[u][t] + u.linearCost*Production[u][t] + u.startUpCost*TurnOn[u][t]))/(sum(u in units) Production[u][t]):0;

float AvgCostByUnit[u in units] =((sum(t in PeriodRange) Production[u][t])!=0)?(sum(t in PeriodRange) (u.constantCost*InUse[u][t] + u.linearCost*Production[u][t] + u.startUpCost*TurnOn[u][t]))/(sum(t in PeriodRange) Production[u][t]):0;
float avgCost = (sum(u in units, t in PeriodRange) Production[u][t]!=0)?(sum(u in units, t in PeriodRange) (u.constantCost*InUse[u][t] + u.linearCost*Production[u][t] + u.startUpCost*TurnOn[u][t]))/(sum(u in units, t in PeriodRange) Production[u][t]):0;

//Spinning Reserve Requirement Data
float SpinningReserveRequirement[PeriodRange]=[ l.period.id: (parameters.reservePercent/100)*l.load | l in loads];


execute UPDATE_RESULTS {
  for(var u in units) {
    for (var t in PeriodRange) {
      production.add(u, t, Production[u][t]);
      if (InUse[u][t] == 1)
        inUse.add(u, t, InUse[u][t]);
      if (TurnOn[u][t] == 1)
        turnOn.add(u, t, TurnOn[u][t]);
      if (TurnOff[u][t] == 1)
        turnOff.add(u, t, TurnOff[u][t]);
      status.add(u, t, Status[u][t]);
      statusString.add(u, t, StatusString[u][t]);
    }
  }    
  for (t in PeriodRange) {
    spinningReserve.add(t, SpinningReserve[t]);
    nbPlantsOnline.add(t, NbPlantsOnline[t]);
    avgCostByPeriod.add(t, AvgCostByPeriod[t]);
    spinningReserveRequirement.add(t, SpinningReserveRequirement[t]);
  }
  for (u in units) {
    utilizationOperating.add(u, UtilizationOperating[u]);
    utilizationPhysical.add(u, UtilizationPhysical[u]);
    avgCostByUnit.add(u, AvgCostByUnit[u]);
  }
  results.avgCost = avgCost;
}
