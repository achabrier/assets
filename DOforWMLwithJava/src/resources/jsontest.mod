

tuple nurse
{
  string name;
  int seniority;
  int qualification;
  int payRate;
}

tuple spoke
{
  string name;
  int minDepTime;
  int maxArrTime;
}

{nurse} Nurses=...;

{spoke} Spokes=...;

dvar boolean x[Nurses][Spokes];

dvar float obj;
minimize obj;
subject to
{
  obj==sum(n in Nurses,s in Spokes) x[n][s]*n.payRate;
  
  forall(s in Spokes) sum(n in Nurses) x[n][s]>=1;
  
  forall(n in Nurses) sum(s in Spokes) x[n][s]<=1;
}

tuple result
{
  string nurse;
  string spoke;
}

{result} results={<n.name,s.name> | n in Nurses, s in Spokes : x[n][s]==1};

execute
{
  writeln(obj);
  writeln(results);
}


