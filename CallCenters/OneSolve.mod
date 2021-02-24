include "inputEngine.mod";  

//
execute {

    writeln("callObjects = " + callObjects.size + ";"); // OK
    writeln("days = " + days.size + ";");           // OK
 	writeln("domains = " + domains.size + ";"); // OK
 	writeln("favoredCallObject = " + favoredCallObject + ";"); // OK
    writeln("parOfDays = " + partOfDays.size + ";"); // OK
    writeln("references = " + references.size + ";"); // OK
    writeln("rtts = " + rtts.size + ";");       // OK
    writeln("shifts = " + shifts.size + ";");         // OK
    writeln("timeSlots = " + timeSlots.size + ";"); // OK
    writeln("workedSaturdayMorning = " + workedSaturdayMorning.size + ";"); // OK
    writeln("workedSaturday = " + workedSaturday.size + ";"); // OK
    writeln("activities = " + activities.size + ";"); // OK
    writeln("callHistoryDuration = " + callHistoryDuration.size + ";"); // OK
    writeln("demands = " + demands.size + ";"); // OK
    writeln("resources = " + resources.size + ";"); // OK
    writeln("competences = " + competences.size + ";");
    writeln("resourceAbsences = " + resourceAbsences.size + ";");
    writeln("resourceRecoveries = " + resourceRecoveries.size + ";"); //OK

}

execute CPX_PARAM {
    cplex.epgap = 0.01;
}

int handleWeek=0;  
int usePreciseLoad=0;
int tiLim = 120;

execute READ_PARAMETERS {
	handleWeek = parameters.find("handleWeek").value;
	writeln("handleWeek="+handleWeek);
	usePreciseLoad = parameters.find("usePreciseLoad").value;
	writeln("usePreciseLoad="+usePreciseLoad);
	tiLim = parameters.find("tiLim").value;
	writeln("tiLim="+tiLim);
	cplex.tiLim = tiLim;
}

int SATURDAY = 5;

// recovery when working on saturday
 // no entry here -> resource cannot work on saturday 
 tuple ResRecovery {
 	key string resourceId;
 	int morning; // Monday to Friday
 	int isAfternoon; // if 0 then only morning. if 1 then afternoon
 	int fullDay; // Monday to Friday
 	int isAllDay; // if 0 then no value for the full day. if 1, value is present
 }

{ResRecovery} resRecoveries = { <r.resourceId, r.morningIndex, r.isAfternoon, r.fullDayIndex, r.isAllDay> |
                                                  r in resourceRecoveries };
sorted {int} OriginalWeeks = { c.week | c in callHistoryDuration };
int weekNo = card(OriginalWeeks);

TResources ResourceArray[r in resources] = r;

tuple RttPlan {
   key string rttId;
   int halfHours;
   int weekNo;
};

{RttPlan} rttPlans;

tuple RttDay {
   key string rttId;
   key int weekType;
   key int day;
   int partOfDay; // 0 = matin 1 = apres-midi 2 = toute la journee
}

{RttDay} rttDays;

sorted {RttDay} WeekDayPerRttArray[r in rttPlans] = { rd | rd in rttDays : rd.rttId == r.rttId }; 

execute {
  function computeHalfHours(strBuffer) {
    var strSplit = strBuffer.split("-");      
    // var strHours = trimNumeric(strSplit[0]);         
    var strHours = strSplit[0];         
    var hours = parseInt(strHours.substring(0, 2));
    var halfHours = (2*hours) + ((strHours.indexOf(",") != (-1))?1:0); 
    return halfHours;
  }      
  for (var rtt in rtts) {
     var strId = rtt.id;
     var strDetail = rtt.details;
     var halfHours =  computeHalfHours(strDetail);
     var weekNo = 1;
     if (halfHours >= 80)
       weekNo = 2;
     rttPlans.add(strId, halfHours, weekNo);
     
     var strSplitDay = strDetail.split(" "); 
     var strDays = strSplitDay[1];
     var i = 0;
     
     while (i < strDays.length) {
       var day = i % 7;
       var weekType = Math.floor(i / 7);
       var partDay = 2;
       var currentSubstring = strDays.substring(i, i+1);
       if (currentSubstring != "-") {
         if (currentSubstring == "m") {
            partDay = 0;
         }
         else if (currentSubstring == "a") {
            partDay = 1;
         }
         rttDays.add(strId, weekType, day, partDay); 
       }
       i++;
     } // end while         
   } // endFor
}

RttPlan RttPlanArray[ r in rttPlans ] = r; 

float  NbHoursPerWeek[r in resources] = RttPlanArray[<r.rttId>].halfHours / RttPlanArray[<r.rttId>].weekNo; 
int firstWeek = first(OriginalWeeks);

int plannedWeekNo = 1; // On ne planifie qu'un week a la fois (depuis le handleWeek courant)
    
{int} Weeks = { i | i in (firstWeek + handleWeek)..(firstWeek+handleWeek+plannedWeekNo-1) };
 
int remainingWeek = weekNo - handleWeek;

// Only take slot form week to plan
sorted {TCallObjectSlot} CallObjectSlots = 
    { <o.objectId, o.week, o.dayIndex, o.timeSlotIndex, o.duration, d.numberCalls> |                                   
       o in callHistoryDuration,
       d in demands :
       o.objectId == d.objectId &&
       o.week   == d.week && 
       o.dayIndex    == d.dayIndex &&
       o.timeSlotIndex == d.timeSlotIndex &&
       o.week in Weeks 
};

 
sorted {TCallObjectSlot} FavoredCallObjectSlots = 
    { <o.objectId, o.week, o.dayIndex, o.timeSlotIndex, o.duration, d.numberCalls> |                                   
       o in callHistoryDuration,
       d in demands :
       o.objectId == favoredCallObject.callObjectId &&
       o.objectId == d.objectId &&
       o.week   == d.week && 
       o.dayIndex    == d.dayIndex &&
       o.timeSlotIndex == d.timeSlotIndex &&
       o.week in Weeks 
};

// Only consider days with existing calls
{int} realDays = { c.day | c in CallObjectSlots : c.numberCalls > 0 };
{int} daysInWeek = { i | i in 0..SATURDAY};
{int} enforcedVacations = daysInWeek diff realDays;

// For a given shit, only consider slots where there is work
sorted {TTimeSlots} SlotPerShiftArray[s in shifts] = { t | a in activities, t in timeSlots : a.shiftId == s.id && a.timeSlotIndex == t.index && a.activity == 1 };
int NbSlotsPerShift[s in shifts] = card(SlotPerShiftArray[s]);

sorted {TShifts} realShifts = { s | s in shifts : card(SlotPerShiftArray[s]) > 0 };

sorted {TTimeSlots} MorningSlotPerShiftArray[s in realShifts] = 
      { t | a in activities, t in timeSlots : a.shiftId == s.id && a.timeSlotIndex == t.index && a.activity == 1 && a.timeSlotIndex <= 25 };

sorted {TTimeSlots} AfternoonSlotPerShiftArray[s in realShifts] = 
      { t | a in activities, t in timeSlots : a.shiftId == s.id && a.timeSlotIndex == t.index && a.activity == 1 && a.timeSlotIndex >= 28 };

sorted {TShifts} MorningShifts = { s | s in realShifts : card(AfternoonSlotPerShiftArray[s]) == 0 };
sorted {TShifts} AfternoonShifts = { s | s in realShifts : card(MorningSlotPerShiftArray[s]) == 0 };

// Create sets with Morning shifts
// 8h30 -> 13h
{TShifts} TheMorningShift =  { s | s in MorningShifts : first(MorningSlotPerShiftArray[s]).index == 17 &&
     last( MorningSlotPerShiftArray[s]).index == 25};
 // 8h30 -> 17h
{TShifts} TheLongAllDayShift = { s | s in realShifts : first (SlotPerShiftArray[s]).index == 17 && 
     last(SlotPerShiftArray[s]).index == 33};
// 9h -> 17h
{TShifts} TheMediumAllDayShift = { s | s in realShifts : first (SlotPerShiftArray[s]).index == 18 && 
     last(SlotPerShiftArray[s]).index == 33};
 
{TShifts} setOfShiftsCoveringPause = { s | s in realShifts : <26> in SlotPerShiftArray[s] && <27> in SlotPerShiftArray[s]}; 

tuple ResourceAbsenceModel {
  key string resourceId;  
  key int week; // relative to start of plan
  key int day; // relative to week
  int partOfDayValue; // 0 == morning, 1 == afternoon, 2 == day
}
{ResourceAbsenceModel} initialResourceAbsenceModel = 
  { <ra.resourceId, ra.weekId, ra.dayIndex, ra.partOfDayIndex> | ra  in resourceAbsences};
  
// Consider resources absent during week
{ResourceAbsenceModel} enforcedResourceAbsenceModel = 
  { <r.id, handleWeek, d, 2> | r in resources, d in enforcedVacations};
   
sorted {ResourceAbsenceModel} resourceAbsenceModel = { r | r in initialResourceAbsenceModel : r.week == handleWeek}
  union  enforcedResourceAbsenceModel;

{ResourceAbsenceModel} 
  resourceWorkingWeek = { <r.id, handleWeek, rtt.day, rtt.partOfDay> | 
                             r in resources,
                             rtt in rttDays :
                             rtt.rttId == r.rttId && 
                             rtt.weekType == ((card(WeekDayPerRttArray[<rtt.rttId>]) <= SATURDAY)?0:(handleWeek mod 2))
                             };

// remaining working days not absent
{ResourceAbsenceModel} resourceRemainingDays = resourceWorkingWeek diff resourceAbsenceModel;
{ResourceAbsenceModel} resourceConsumedDays = resourceAbsenceModel inter resourceWorkingWeek; 

{ResourceAbsenceModel} remainingDaysPerResource[r in resources] = { j | j in resourceRemainingDays : j.resourceId == r.id };
{ResourceAbsenceModel} consumedDaysPerResource[r in resources] = { j | j in resourceConsumedDays : j.resourceId == r.id };
// Number of absence slots (15 1/2 hours for a day and 8 1/2 horus for half a day
int consumedSlotNumber[r in resources] = sum ( ra in  consumedDaysPerResource[r]) 
   ((NbHoursPerWeek[<r.id>] == 75)?((ra.partOfDayValue == 2)?15:8):
       ((NbHoursPerWeek[<r.id>] == 70)?(((ra.partOfDayValue == 2)?14:7)):
          (((ra.partOfDayValue == 2)?14:7))));

// Resource absences covering all day
{ResourceAbsenceModel} DayAbsencePerResource[ r in resources ] = { a | a in  resourceAbsenceModel : a.resourceId == r.id && a.partOfDayValue == 2 };
{ResourceAbsenceModel} MorningAbsencePerResource[ r in resources ] = { a | a in  resourceAbsenceModel : a.resourceId == r.id && a.partOfDayValue == 0 };
{ResourceAbsenceModel} AfternoonAbsencePerResource[ r in resources ] = { a | a in  resourceAbsenceModel : a.resourceId == r.id && a.partOfDayValue == 1 };

{string} ResourceIds = { r.id | r in resources };
// Resources that cannot recover on Saturday don't work on Saturday
{string} NotSaturdayResourceId = ResourceIds diff { r.resourceId | r in resourceRecoveries};

{string} ResourcesIdsWithAbsences = { r.resourceId | r in resourceAbsenceModel};
{string} ResourcesIdsWithoutAbsence =   ResourceIds diff   ResourcesIdsWithAbsences;       

// For one particular day several shifts are possible
// But just one will be selected at the end
tuple AlternativeShift {
    key string resourceId;
    key int week;
    key int day;
    key string shiftId;
};

sorted {AlternativeShift} AlternativeShifts = 
    { <rId, w, d, s.id> | rId in ResourcesIdsWithoutAbsence, w in Weeks, d in realDays, s in realShifts  } union
    { <rId, w, d, s.id> | 
      rId in ResourcesIdsWithAbsences, 
      w in Weeks, 
      d in realDays, 
      s in realShifts :
      !(<rId, w, d> in  DayAbsencePerResource[<rId>]) && 
      !((s in MorningShifts) && (<rId, w, d> in MorningAbsencePerResource[<rId>])) &&
      !((s in AfternoonShifts) && (<rId, w, d> in AfternoonAbsencePerResource[<rId>])) 
      };

sorted {AlternativeShift} AlternativeMorningShifts = { a | a in AlternativeShifts, s in MorningShifts : a.shiftId == s.id };
sorted {AlternativeShift} AlternativeAfternoonShifts = { a | a in AlternativeShifts, s in AfternoonShifts : a.shiftId == s.id };
sorted {AlternativeShift} AlternativeAllDayShifts = AlternativeShifts diff (AlternativeMorningShifts union AlternativeAfternoonShifts);
sorted {AlternativeShift} TheAlternativeMorningShifts = { a | a in AlternativeShifts, s in TheMorningShift: a.shiftId == s.id };
sorted {AlternativeShift} TheAlternativeLongAllDayShifts =
   { a | a in AlternativeShifts, s in TheLongAllDayShift: a.shiftId == s.id };
sorted {AlternativeShift} TheAlternativeMediumAllDayShifts =
   { a | a in AlternativeShifts, s in TheMediumAllDayShift: a.shiftId == s.id };
           
tuple ResourceWorkDay { 
   string   resourceId;
   int      week;
   int      day;
};

tuple WorkDay { 
   int      week;
   int      day;
};

{ResourceWorkDay} ResourceWorkDays = { <r.id, w, d> | r in resources, w in Weeks, d in realDays};

{WorkDay} WorkDays = { <wd.week, wd.day> |  wd in ResourceWorkDays};

{AlternativeShift} ArrayShift[wd in ResourceWorkDays] = 
   { a | a in AlternativeShifts : a.resourceId == wd.resourceId && a.week == wd.week && a.day == wd.day };   

{AlternativeShift} ArrayMorningShift[wd in ResourceWorkDays] = 
    { a | a in AlternativeMorningShifts : a.resourceId == wd.resourceId && a.week == wd.week && a.day == wd.day };
{AlternativeShift} ArrayTheMorningShift[wd in ResourceWorkDays] = 
    { a | a in TheAlternativeMorningShifts : a.resourceId == wd.resourceId && a.week == wd.week && a.day == wd.day };
{AlternativeShift} ArrayAfternoonShift[wd in ResourceWorkDays] = 
    { a | a in AlternativeAfternoonShifts : a.resourceId == wd.resourceId && a.week == wd.week && a.day == wd.day };
{AlternativeShift} ArrayAllDayShift[wd in ResourceWorkDays] = 
    { a | a in AlternativeAllDayShifts : a.resourceId == wd.resourceId && a.week == wd.week && a.day == wd.day };
{AlternativeShift} ArrayTheLongAllDayShift[wd in ResourceWorkDays] = 
    { a | a in TheAlternativeLongAllDayShifts : a.resourceId == wd.resourceId && a.week == wd.week && a.day == wd.day };
{AlternativeShift} ArrayTheMediumAllDayShift[wd in ResourceWorkDays] = 
    { a | a in TheAlternativeMediumAllDayShifts : a.resourceId == wd.resourceId && a.week == wd.week && a.day == wd.day };

{AlternativeShift} ArrayAllDayShiftWithRTT[wd in ResourceWorkDays] = 
   { a | a in AlternativeAllDayShifts, rtt in rttDays : 
     a.resourceId == wd.resourceId && 
     a.week == wd.week && 
     a.day == wd.day &&
     ResourceArray[<a.resourceId>].rttId == rtt.rttId &&     
     a.day == rtt.day && 
     rtt.weekType == ((card(WeekDayPerRttArray[<rtt.rttId>]) <= SATURDAY)?(0):(a.week mod 2)) && 
     rtt.partOfDay == 2     
     };
   
{AlternativeShift} ArrayMorningShiftWithRTT[wd in ResourceWorkDays] = 
   { a | a in AlternativeMorningShifts, rtt in rttDays : 
     a.resourceId == wd.resourceId && 
     a.week == wd.week && 
     a.day == wd.day && 
     ResourceArray[<a.resourceId>].rttId == rtt.rttId &&     
     a.day == rtt.day &&
     rtt.weekType == ((card(WeekDayPerRttArray[<rtt.rttId>]) <= SATURDAY)?(0):(a.week mod 2)) && 
     rtt.partOfDay == 0     
     };

{AlternativeShift} ArrayAfternoonShiftWithRTT[wd in ResourceWorkDays] = 
   { a | a in AlternativeAfternoonShifts, rtt in rttDays : 
     a.resourceId == wd.resourceId && 
     a.week == wd.week && 
     a.day == wd.day && 
     ResourceArray[<a.resourceId>].rttId == rtt.rttId &&     
     a.day == rtt.day &&
     rtt.weekType == ((card(WeekDayPerRttArray[<rtt.rttId>]) <= SATURDAY)?(0):(a.week mod 2)) && 
     rtt.partOfDay == 1     
     };

tuple RTTResourceDay {
   key string resourceId;
   key int day;
   int partOfDay;
};

{RTTResourceDay} rttResourceDays =
    { <r.id, rtt.day, rtt.partOfDay>  |
        r in resources,
        rtt in rttDays,
        d in realDays:
	      rtt.day == d &&
    	  r.rttId == rtt.rttId &&
		  rtt.weekType == ((card(WeekDayPerRttArray[<rtt.rttId>]) <= SATURDAY)?(0):(handleWeek mod 2))
      };


{AlternativeShift} ArrayShiftPerWorkDay[w in WorkDays] = { a | a in AlternativeShifts : a.week == w.week && a.day == w.day };   

tuple FixedCompetence {
  key string objectId;
  key string resourceId;
}

{FixedCompetence} FixedCompetences = { <c, r> | <c, r> in competences, co in callObjects : c == co.id };

{string}  CompetenceArray[r in resources] = { fc.objectId | fc in FixedCompetences : fc.resourceId == r.id };

{string} usedSkills = { c.objectId | c in CallObjectSlots };
{FixedCompetence} UsedFixedCompetences = { f | f in FixedCompetences, s in usedSkills : f.objectId == s };
{string}  UsedCompetenceArray[r in resources] = { f.objectId | f in UsedFixedCompetences : f.resourceId == r.id };



TCallObjectSlot CallObjectSlotArray[c in  CallObjectSlots] = c;

tuple FixedSlotSkill {
  key string resourceId;
  key string objectId;
  key int day;
  key int slotIndex;
};

sorted {FixedSlotSkill} fixedSlotSkills = { <r.id, c.objectId, c.day, c.slotIndex> | 
                                     c in CallObjectSlots, r in resources, uc in UsedCompetenceArray[r]: 
                                     c.objectId == uc };
                                     
float SkillDurationPerFixedSlot[f in fixedSlotSkills] = CallObjectSlotArray[<f.objectId, handleWeek, f.day, f.slotIndex>].duration;

float relativeDurationPerSlotSkill[f in fixedSlotSkills] = 
  0.01 * ceil(100.0 * (
  SkillDurationPerFixedSlot[f] /sum(s in fixedSlotSkills: s.day == f.day && s.slotIndex == f.slotIndex && s.resourceId == f.resourceId)  SkillDurationPerFixedSlot[s]));

// All alternative shifts to cover a slot
{AlternativeShift} AlternativeShiftPerCallObjectSlot[c in CallObjectSlots] = 
   {  a | 
      a in AlternativeShifts :
      c.objectId in CompetenceArray[<a.resourceId>] &&
      a.week == c.week &&
      a.day == c.day &&            
     (<c.slotIndex> in SlotPerShiftArray[<a.shiftId>] || 
     (<c.slotIndex -1> in SlotPerShiftArray[<a.shiftId>])) };
      
{AlternativeShift} AlternativeShiftsCoveringPause[w in WorkDays] =
     { a |  a in AlternativeShifts, s in setOfShiftsCoveringPause :
       a.week == w.week && 
       a.day == w.day &&
       a.shiftId == s.id };


// Some specific management of Saturdays
{string} ResourceNotWorkingOnMorningSaturdayAM = 
        { rc.resourceId |                     
          rc in resRecoveries,
          ra in resourceAbsenceModel:
          ra.resourceId == rc.resourceId &&
          ra.day == rc.morning &&
          ra.week == handleWeek && 
          (ra.partOfDayValue == 1 || ra.partOfDayValue == 2) && 
          (rc.isAfternoon == 1) 
          }; 

{string} ResourceNotWorkingOnMorningSaturday = 
        { rc.resourceId |                     
          rc in resRecoveries,
          ra in resourceAbsenceModel:
          ra.resourceId == rc.resourceId &&
          ra.day == rc.morning &&
          ra.week == handleWeek &&           
          (rc.isAfternoon != 1) }; 

{string} ResourceNotWorkingOnSaturday = 
        { rc.resourceId |                     
          rc in resRecoveries,
          ra in resourceAbsenceModel:
          ra.resourceId == rc.resourceId &&
          ra.day == rc.fullDay &&
          ra.week == handleWeek &&           
          (rc.isAllDay == 1) }; 

{string} ResourceNotWorkingOnSaturdayDueToRecovery = 
   ResourceNotWorkingOnMorningSaturdayAM union ResourceNotWorkingOnMorningSaturday union ResourceNotWorkingOnSaturday; 

{string} resourceNotWorkingOnSaturday = NotSaturdayResourceId  union ResourceNotWorkingOnSaturdayDueToRecovery;
{RTTResourceDay} rttResourceDayNotWorkingOnSaturday = { rtt | rtt in rttResourceDays, rId in resourceNotWorkingOnSaturday : rtt.resourceId == rId};

{string} setOfNotWorkingResources = resourceNotWorkingOnSaturday union {c.resourceId | c in workedSaturday};
   
tuple ResourceWeek {
  key string resourceId;
  int week;
};

// resourceWeeks not consider absence 
{ResourceWeek} resourceWeeks = { <r.id, w> | r in resources, w in Weeks : card(remainingDaysPerResource[r]) >= 1 };

dvar int WorkVar[w in ResourceWorkDays] in 0..1; 
dvar int AlternativeVar[v in AlternativeShifts] in 0..1;
dvar float slackVar[v in CallObjectSlots] in 0..5000;
dvar int   slackPresenceVar[v in CallObjectSlots] in 0..2; // au moins deux agents pr�sents par slot et specialit�
dvar int slackWeekVarLower[w in resourceWeeks] in 0..112;
dvar int slackWeekVarUpper[w in resourceWeeks] in 0..112;
dvar int slackRTTResourceDay[r in rttResourceDays] in 0..1;
int resourceNo = card(resources);
dvar int slackSaterdayLess [ w in Weeks] in 0..resourceNo;
dvar int slackSaterdayMore [ w in Weeks] in 0..resourceNo;
dexpr int slackSaturday = sum(w in Weeks) (slackSaterdayLess[w] +  slackSaterdayMore[w]);
dexpr float slackLoad = sum (c in CallObjectSlots) slackVar[c];
dexpr float slackFavoredCallObject = sum (c in FavoredCallObjectSlots) slackVar[c];
dexpr int   slackPresence = sum (c in CallObjectSlots) slackPresenceVar[c];
dexpr int slackWeekLowerUpper = sum (wr in resourceWeeks) (slackWeekVarLower[wr] + slackWeekVarUpper[wr]);
dexpr int slotsPerWeek[rw in resourceWeeks] =  sum(a in AlternativeShifts: a.resourceId == rw.resourceId && a.week == rw.week) AlternativeVar[a] * NbSlotsPerShift[<a.shiftId>];
int rttResourceDaysNo = card(rttResourceDays);
dexpr int slackWeekType = rttResourceDaysNo - sum(rtt in rttResourceDays) slackRTTResourceDay[rtt];
dexpr int SaturdayMorningShiftVar[r in resRecoveries] = sum (a in ArrayMorningShift[<r.resourceId, handleWeek, SATURDAY>]) AlternativeVar[a];

// Cost function
//    slackLoad 		  : slack between load and coverage
//    slackWeekLowerUpper : slack between weekly number of hours
//    slackSaturday  	  : slack on Saturday fairness
//    slackPresence  	  : slack on at least 2 agens per slot and call type
//    slackWeekType  	  : slack with respect to typical week

minimize slackLoad + slackWeekLowerUpper + slackSaturday + slackPresence + slackWeekType
     				+ favoredCallObject.favorFactor * slackFavoredCallObject ; 

constraints {

    // At most one shift is assigned to the alternative at the end
    forall (wd in ResourceWorkDays) {
		presenceCt :
			sum (a in ArrayShift[wd]) AlternativeVar[a] == WorkVar[wd];          
    }    
    
    if (SATURDAY in realDays) {
      forall (rId in NotSaturdayResourceId) {
        notSaturdayWorking:
           sum(a in ArrayShift[<rId, handleWeek, SATURDAY>]) AlternativeVar[a] == 0;
      }
    }
      
    if (SATURDAY in realDays) {
      // Ne pas faire travailler un agent le samedi si sa r�cup�ration tombe un jour de cong�s!
      forall (rId in ResourceNotWorkingOnSaturdayDueToRecovery) {
         notSaturdayWorkingDueToRecovery:
         sum (a in ArrayShift[ <rId, handleWeek, SATURDAY> ]) AlternativeVar[a] == 0;
      }
    }
    
    if (SATURDAY in realDays) {
    
      // Recovery constraints
      forall (r in resourceRecoveries) {
        // No work other than The morning shift
        sum (a in (ArrayMorningShift[<r.resourceId, handleWeek, SATURDAY>] diff ArrayTheMorningShift[<r.resourceId, handleWeek, SATURDAY>]))
               AlternativeVar[a]  == 0;      
        // Not all shifts possible on Saturday
        if (NbHoursPerWeek[<r.resourceId>] == 70) { // 35 hours
          sum (a in (ArrayAllDayShift[<r.resourceId, handleWeek, SATURDAY>] diff ArrayTheMediumAllDayShift[<r.resourceId, handleWeek, SATURDAY>])) 
              AlternativeVar[a]  == 0;        
        } 
        else if (NbHoursPerWeek[<r.resourceId>] == 75) { // 35 hours
           sum (a in (ArrayAllDayShift[<r.resourceId, handleWeek, SATURDAY>] diff ArrayTheLongAllDayShift[<r.resourceId, handleWeek, SATURDAY>])) 
              AlternativeVar[a]  == 0;        
        }                         
                
        // If working on Satruday then rest on given day
        if (r.isAfternoon == 1) {
            sum (a in ArrayTheMorningShift[<r.resourceId, handleWeek, SATURDAY>]) AlternativeVar[a] <=
              sum (a in ArrayMorningShift[<r.resourceId, handleWeek, r.morningIndex>]) AlternativeVar[a]; 
            sum (a in ArrayTheMorningShift[<r.resourceId, handleWeek, SATURDAY>]) AlternativeVar[a] + 
              sum (a in ArrayAfternoonShift[<r.resourceId, handleWeek, r.morningIndex>]) AlternativeVar[a] <= 1;                                                                          
      }       
      else { // recover all day
          sum (a in ArrayTheMorningShift[<r.resourceId, handleWeek, SATURDAY>]) AlternativeVar[a] + 
            sum(a in ArrayShift[<r.resourceId, handleWeek, r.morningIndex>]) AlternativeVar[a] <= 1;
      }
      if (r.isAllDay == 1) {
           // if work all day, recover all given day 
           sum (a in ArrayAllDayShift[<r.resourceId, handleWeek, SATURDAY>]) AlternativeVar[a] +
             sum(a in ArrayShift[<r.resourceId, handleWeek, r.fullDayIndex>]) AlternativeVar[a] <= 1;
      }   
      else { // if not working on saturday 
           sum (a in ArrayAllDayShift[<r.resourceId, handleWeek, SATURDAY>]) AlternativeVar[a] == 0;       
      }         
      
       // No shift on saturday afternoon only
      sum (a in ArrayAfternoonShift[<r.resourceId, handleWeek, SATURDAY>]) AlternativeVar[a] <= 0;
     }
    }
    
    if (SATURDAY in realDays) {
    
      forall (rrd in rttResourceDayNotWorkingOnSaturday) {
         enforceSlackAllDay:
         if (1 <= card(ArrayAllDayShiftWithRTT[<rrd.resourceId, handleWeek, rrd.day>])) {
           if (rrd.day != 0 || (rrd.day == 0 && !(<rrd.resourceId> in workedSaturdayMorning))) {
              1 == slackRTTResourceDay[rrd]; 
           }
         }         
         enforceSlackMorningDay:
         if (1 <= card(ArrayMorningShiftWithRTT[<rrd.resourceId, handleWeek, rrd.day>])) {
           if (rrd.day != 0 || (rrd.day == 0 && !(<rrd.resourceId> in workedSaturdayMorning))) {         
              1 == slackRTTResourceDay[rrd];
           } 
         }
         enforceSlackAfternoonDay:
         if (1 <= card(ArrayAfternoonShiftWithRTT[<rrd.resourceId, handleWeek, rrd.day>])) {
           1 == slackRTTResourceDay[rrd];
         } 
      }
    
      forall (rrd in rttResourceDays) {
         enforceAllDay:
         if (1 <= card(ArrayAllDayShiftWithRTT[<rrd.resourceId, handleWeek, rrd.day>])) {
           sum (a in ArrayAllDayShiftWithRTT[<rrd.resourceId, handleWeek, rrd.day>]) AlternativeVar[a] == slackRTTResourceDay[rrd]; 
         }         
         enforceMorningDay:
         if (1 <= card(ArrayMorningShiftWithRTT[<rrd.resourceId, handleWeek, rrd.day>])) {
           sum (a in ArrayMorningShiftWithRTT[<rrd.resourceId, handleWeek, rrd.day>]) AlternativeVar[a] == slackRTTResourceDay[rrd]; 
         }
         enforceAfternoonDay:
         if (1 <= card(ArrayAfternoonShiftWithRTT[<rrd.resourceId, handleWeek, rrd.day>])) {
           sum (a in ArrayAfternoonShiftWithRTT[<rrd.resourceId, handleWeek, rrd.day>]) AlternativeVar[a] == slackRTTResourceDay[rrd];
         } 
      }
    }
    
    // 50 % rule
    // At most one shift is assigned to the alternative at the end
    forall (wd in WorkDays) {
       fiftyPercentCt :
       0.5 * sum (a in ArrayShiftPerWorkDay[wd]) AlternativeVar[a] <= sum (a in AlternativeShiftsCoveringPause[wd]) AlternativeVar[a];          
    }
    
    // At least Two workers per worked shift
    forall (c in CallObjectSlots : c.numberCalls >= 1) {
       couverturePresence: 
          2 - slackPresenceVar[c] <= sum (a in AlternativeShiftPerCallObjectSlot[c]) AlternativeVar[a];          
    } 

    if (usePreciseLoad == 0) {
      forall (c in CallObjectSlots) {
         couvertureImprecise : 
            ((c.duration * c.numberCalls) / 1800) <= (sum (a in AlternativeShiftPerCallObjectSlot[c]) AlternativeVar[a]) + slackVar[c];          
       }
       forall (c in FavoredCallObjectSlots) {
         couvertureImpreciseMLE : 
            ((c.duration * c.numberCalls) / 1800) <= (sum (a in AlternativeShiftPerCallObjectSlot[c]) AlternativeVar[a]) + slackVar[c];          
       }
    } 
    else {
      forall (c in CallObjectSlots) {
        couverturePrecise : 
          ((c.duration * c.numberCalls)/1800) <= 
            (sum (a in AlternativeShiftPerCallObjectSlot[c]) (AlternativeVar[a] * relativeDurationPerSlotSkill[<a.resourceId, c.objectId, c.day, c.slotIndex>])) + slackVar[c];                    
      }
      forall (c in FavoredCallObjectSlots) {
        couverturePreciseFavored : 
          ((c.duration * c.numberCalls)/1800) <= 
            (sum (a in AlternativeShiftPerCallObjectSlot[c]) (AlternativeVar[a] * relativeDurationPerSlotSkill[<a.resourceId, c.objectId, c.day, c.slotIndex>])) + slackVar[c];                    
      }
    }

    
    // Nb of hours per week: 
    // Integrate the number of day off in the constraint 
    forall (rw in resourceWeeks) {
      nbHalfHoursPerWeek:
      (RttPlanArray[<ResourceArray[<rw.resourceId>].rttId>].halfHours /  RttPlanArray[<ResourceArray[<rw.resourceId>].rttId>].weekNo)
        + slackWeekVarUpper[rw]        
       == consumedSlotNumber[<rw.resourceId>] + slackWeekVarLower[rw] + slotsPerWeek[rw];    
    }
   
   forall (r in ResourceWorkDays : <r.resourceId> in workedSaturday && r.day == SATURDAY) {
       WorkVar[r] == 0;           
   }
   
   // Recover Monday Morning for those who worked on Saturday Morning
    forall (r in ResourceWorkDays : <r.resourceId> in workedSaturdayMorning && r.day == 0) {    
       sum (a in ArrayMorningShift[<r.resourceId, handleWeek, 0>]) AlternativeVar[a] <= 0;
       sum (a in ArrayAllDayShift[<r.resourceId, handleWeek, 0>]) AlternativeVar[a] <= 0;       
   }
      
   forall(w in Weeks) {
      sum(r in ResourceWorkDays: r.week == w && r.day == SATURDAY) WorkVar[r] == 
         ((resourceNo - card(setOfNotWorkingResources)) div remainingWeek) + slackSaterdayMore[w] - slackSaterdayLess[w] ;         
   }

}

tuple WeekCovery {
   string resourceId;
   int week;
   float halfHourPerWeek;
   int consumed;
   int slackVar;
   int slotVar;
}

{WeekCovery} weekCoveries = { <rw.resourceId, rw.week,  
                                (RttPlanArray[<ResourceArray[<rw.resourceId>].rttId>].halfHours /  RttPlanArray[<ResourceArray[<rw.resourceId>].rttId>].weekNo),
                                 consumedSlotNumber[<rw.resourceId>],
                                  slackWeekVarLower[rw], 
                                  slotsPerWeek[rw]> |
                              rw in resourceWeeks };
                                                             
TResources GetResource[r in resources] = r;
TShifts    GetShift[s in shifts] = s;

{TSchedulesSol} weekSchedulesSol  =
   { < a.resourceId, a.week, a.shiftId, a.day, GetShift[<a.shiftId>].start, GetShift[<a.shiftId>].end> |
     a in AlternativeShifts : AlternativeVar[a] == 1 };
     
{THoursPerWeeks} weekHoursPerWeeks = 
{ <rw.resourceId, GetResource[<rw.resourceId>].rttId, GetResource[<rw.resourceId>].domainId, rw.week,  ((slotsPerWeek[rw] + consumedSlotNumber[<rw.resourceId>])* 0.5), 
  ((RttPlanArray[<ResourceArray[<rw.resourceId>].rttId>].halfHours / RttPlanArray[<ResourceArray[<rw.resourceId>].rttId>].weekNo)* 0.5) > | 
  rw in resourceWeeks };

{TSchedulesSol} schedulesSolCoveringPause =
   { < a.resourceId, a.week, a.shiftId, a.day, GetShift[<a.shiftId>].start, GetShift[<a.shiftId>].end> |
     a in AlternativeShifts, s in  setOfShiftsCoveringPause: a.shiftId == s.id && AlternativeVar[a] == 1 };

tuple SumPauseCovering {
   int sumCovering;
   int sumTotal;
};

SumPauseCovering CoverTotal[wd in WorkDays] = < sum (a in AlternativeShiftsCoveringPause[wd]) AlternativeVar[a], sum (a in ArrayShiftPerWorkDay[wd]) AlternativeVar[a] >;

{TPercentagePeopleAtPause} weekPercentageAtPause = 
    { <wd.week, wd.day, ((CoverTotal[wd].sumTotal != 0)?(CoverTotal[wd].sumCovering / CoverTotal[wd].sumTotal):0)> | wd in WorkDays };

{TWorkedSaturday} weekWorkDay = { <w.resourceId> | w in ResourceWorkDays : w.day == SATURDAY && WorkVar[w] == 1 };

{TWorkedSaturday} weekWorkedSaturday  = 
   { <sol.resourceId>  | 
      sol in weekSchedulesSol :
      sol.day == SATURDAY &&
      sol.week == handleWeek };


{TWorkedSaturdayMorning} weekWorkedSaturdayMorning = 
   { <sol.resourceId>  | 
      sol in weekSchedulesSol, s in MorningShifts :
      sol.day == SATURDAY &&
      sol.week == handleWeek &&           
      sol.shiftId == s.id };
       
sorted {TCouverture} weekCouverture = 
   { <c.objectId, c.week, c.day, c.slotIndex, 
		((c.duration * c.numberCalls) / 1800), 
     (sum (a in AlternativeShiftPerCallObjectSlot[c]) AlternativeVar[a] * relativeDurationPerSlotSkill[<a.resourceId, c.objectId, c.day, c.slotIndex>])> | c in CallObjectSlots};


{TNbAgentCovery} weekAgentCovery = 
   { <c.objectId, c.week, c.day, c.slotIndex, 
		sum (a in AlternativeShiftPerCallObjectSlot[c]) AlternativeVar[a]> | c in CallObjectSlots : c.numberCalls >= 1 };
  
dexpr float slackLoadKPI = sum (c in CallObjectSlots) slackVar[c];
dexpr int slackWeekLowerUpperKPI = sum (wr in resourceWeeks) (slackWeekVarLower[wr] + slackWeekVarUpper[wr]);
dexpr int slackSaturdayKPI = sum(w in Weeks) (slackSaterdayLess[w] +  slackSaterdayMore[w]);
dexpr int slackPresenceKPI = sum (c in CallObjectSlots) slackPresenceVar[c];
dexpr int slackWeekTypeKPI = rttResourceDaysNo - sum(rtt in rttResourceDays) slackRTTResourceDay[rtt];
dexpr float slackFavoredCallObjectKPI = sum (c in FavoredCallObjectSlots) slackVar[c];

execute {
  writeln("slackFavoredCallObject=" + slackFavoredCallObject);
  writeln("slackLoad = " + slackLoad);
  writeln("slackSaturday = " + slackSaturday);
  writeln("slackPresence = " + slackPresence);
  writeln("slackWeekType = " + slackWeekType);
}

{TSchedulesSol} finalSchedulesSol = {s|s in weekSchedulesSol};
{TCouverture} finalCouverture = {s|s in weekCouverture};