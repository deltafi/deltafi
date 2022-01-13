export type State = {
  searchOptionsState: {
    startTimeDateState: String | null,
    endTimeDateState: String | null,
    actionTypeOptionState: String | null,
    fileNameOptionState: String | null,
    flowOptionState: String | null,
    stageOptionState: String | null,
  }
}

export const state: State = {
  searchOptionsState: {
    startTimeDateState: null,
    endTimeDateState: null,
    actionTypeOptionState: null,
    fileNameOptionState: null,
    flowOptionState: null,
    stageOptionState: null,
  }
};