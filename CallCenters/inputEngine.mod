
// Input

tuple TCallObjects {
  key string id;
};

tuple TDays {
  key int index;
  string label;
};

tuple TDomains {
  key string id;
};

tuple TFavoredCallObject {
  string callObjectId;
  float favorFactor;
};

tuple TPartOfDays {
  key int index;
  string label;
};

tuple TReferences {
  key string startDateForPlan;
  key string startReferenceForDuration;
  key int numberOfWeeksForDuration;
  key string startReferenceForDemand;
  key int numberOfWeeksForDemand;
};

tuple TRtts {
  key string id;
  string details;
};

tuple TShifts {
  key string id;
  float start;
  float end;
};

tuple TTimeSlots {
  key int index;
  string label;
};

tuple TWorkedSaturdayMorning {
  key string resourceId;
};

tuple TWorkedSaturday {
  key string resourceId;
};
{TCallObjects} callObjects = ...;
{TDays} days = ...;
{TDomains} domains = ...;
TFavoredCallObject favoredCallObject = ...;
{TPartOfDays} partOfDays = ...;
{TReferences} references = ...;
{TRtts} rtts = ...;
{TShifts} shifts = ...;
{TTimeSlots} timeSlots = ...;
{TWorkedSaturdayMorning} workedSaturdayMorning = ...;
{TWorkedSaturday} workedSaturday = ...;

tuple TActivities {
  key string shiftId;
  key int timeSlotIndex;
  int activity;
  string timeSlotLabel;
};

tuple TCallHistoryDuration {
  key string objectId;
  key int week;
  key int dayIndex;
  key int timeSlotIndex;
  int duration;
  int numberOfCalls;
  string date;
  string timeSlotLabel;
};

tuple TDemands {
  key string objectId;
  key int week;
  key int dayIndex;
  key int timeSlotIndex;
  int numberCalls;
  string date;
  string timeSlotLabel;
};

tuple TResources {
  key string id;
  string rttId;
  string domainId;
};
{TActivities} activities = ...;
{TCallHistoryDuration} callHistoryDuration = ...;
{TDemands} demands  = ...;
{TResources} resources = ...;

tuple TCompetences {
  key string objectId;
  key string resourceId;
};

tuple TResourceAbsences {
  key string resourceId;
  key int weekId;
  key int dayIndex;
  key int partOfDayIndex;
  string reason;
  string description;
};

tuple TResourceRecoveries {
  key string resourceId;
  int morningIndex;
  int isAfternoon;
  int fullDayIndex;
  int isAllDay;
};
{TCompetences} competences = ...;
{TResourceAbsences} resourceAbsences = ...;
{TResourceRecoveries} resourceRecoveries = ...;

tuple TParameters {
	key string name;
	int value;
};
{TParameters} parameters = ...;
	

// Output

tuple TCouverture {
  key string objectId;
  key int week;
  key int day;
  key int slotIndex;
  float load;
  float couverture;
};


tuple THoursPerWeeks {
  key string resourceId;
  string resourceRttId;
  string resourceDomainId;
  key int week;
  float workLoad;
  float theoriticalHours;
};

tuple TKpis {
  float demandCoverageSlack;
  float weeklyWorkLoadSlack;
  float saturdayWorkLoadSpread;
  float weekTypeSlack;
  float presenceSlack;
  float favoredCallSlack;
};

tuple TNbAgentCovery {
  key string objectId;
  key int week;
  key int day;
  key int slotIndex;
  int nbAgent;
};


tuple TPercentageAtPause {
  key int week;
  key int day;
  float percentagePause;
};

tuple TSchedulesSol {
  key string resourceId;
  key int week;
  key string shiftId;
  key int day;
  float startShift;
  float endShift;
};
{TCouverture} couverture;
{THoursPerWeeks} hoursPerWeeks;
TKpis kpis;
{TNbAgentCovery} nbAgentCovery;
{TPercentageAtPause} percentageAtPause;
{TSchedulesSol} schedulesSol;


tuple TPercentagePeopleAtPause {
    key int week;
    key int day;
    float percentagePause;
};



tuple TCallObjectSlot {
   key string objectId;
   key int week;
   key int day;
   key int slotIndex;
   float   duration;
   int     numberCalls;
};









