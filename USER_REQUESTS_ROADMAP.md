# VitruvianRedux - User Requests Roadmap & Feasibility Assessment

**Date:** 2025-11-13
**Status:** Planning Phase
**Source:** Trello Board Export (UlrcKTrB)

---

## Executive Summary

This document analyzes 11 user feature requests from the Trello board, providing:
- **Feasibility assessment** (Technical complexity, effort, dependencies)
- **Priority recommendation** (User value, votes, urgency)
- **Implementation roadmap** (Phased approach, timeline estimates)
- **Technical analysis** (Architecture impact, code changes required)

**Quick Stats:**
- Total Requests: 11 (1 appears to be duplicate)
- High Priority: 3 requests
- Medium Priority: 5 requests
- Low Priority: 2 requests
- Total Estimated Effort: 18-24 weeks

---

## Request Analysis & Prioritization

### 🔴 **HIGH PRIORITY** (Tier 1 - Weeks 1-6)

#### 1. **Routines Displayed as Individual Exercises in Workout History**
**Request ID:** 69169936271af60ae47bca44
**Votes:** 0 | **Comments:** 0 | **Status:** Bug/Enhancement

**Description:**
When users complete a routine (group of exercises), the workout history should show:
- Option A: The completed routine as a single entry
- Option B: Individual exercises within the routine with correct set counts

**Current Behavior:**
Routines display as individual single exercises without grouping context.

**Feasibility:** ✅ **HIGH** (Medium complexity, critical UX issue)

**Technical Analysis:**
- **Database:** Already has `routineSessionId` and `routineName` fields (WorkoutDatabase v16, added for this exact purpose)
- **Repository:** Need to modify `WorkoutDao` queries to group by `routineSessionId`
- **UI:** Need to update `WorkoutHistoryScreen.kt` to display grouped routine sessions
- **ViewModels:** Minimal changes, just transform grouped data

**Dependencies:**
- None - database schema already supports this

**Effort Estimate:** 1-2 weeks

**Implementation Steps:**
1. Update `WorkoutDao.kt` - Add query to fetch grouped routine sessions
2. Create `RoutineWorkoutSession` data class for grouped display
3. Update `WorkoutHistoryViewModel.kt` - Transform grouped data
4. Update `WorkoutHistoryScreen.kt` UI - Add expandable routine cards
5. Add filtering options (show routines vs individual exercises)

**Priority Justification:**
Critical UX issue that affects how users track their workout progress. Database schema is already prepared for this (v16 migration). This was likely planned but not implemented.

---

#### 2. **Smaller Weight Increments (0.5lb)** ✅ **COMPLETE**
**Request ID:** 6913cadae985d74c66ac4be4
**Votes:** 1 | **Comments:** 0
**Completed:** 2025-11-14

**Description:**
Allow users to set weight increments in 0.5lb steps instead of only 1lb increments.

**Status:** ✅ **IMPLEMENTED**
**Implementation:** `CompactNumberPicker.kt`, `JustLiftScreen.kt`, `ExerciseEditDialog.kt`
- Hardware supports 0.5lb precision (32-bit Float, 0.01kg precision from device)
- UI updated with 0.5lb step for lbs, 0.25kg step for kg
- Smart formatting shows whole numbers without decimals

**Feasibility:** ✅ **VERY HIGH** (Low complexity, quick win)

**Technical Analysis:**
- **Protocol:** BLE protocol sends weight as integer (kg or lbs)
- **UI:** Change weight picker step size from 1.0 to 0.5
- **Data Model:** Check if `Float`/`Double` already used (likely yes)
- **Validation:** Ensure 0.5lb precision supported by protocol

**Dependencies:**
- Verify Vitruvian hardware supports 0.5lb increments
- Check if protocol sends integer lbs or allows decimal

**Effort Estimate:** 1-3 days

**Implementation Steps:**
1. Verify protocol support (check ProtocolBuilder.kt)
2. Update weight picker step size in UI components
3. Update weight display formatting to show ".5" properly
4. Test with actual hardware to verify precision

**Priority Justification:**
Quick win with high user value. Many strength training programs require micro-loading (especially for upper body). Low risk, high reward.

**⚠️ CAUTION:**
Need to verify that Vitruvian hardware actually supports 0.5lb increments. If protocol sends integer pounds, this may not be feasible without firmware changes.

---

#### 3. **Add Option for Each Set to Have Its Own Rest Time** ✅ **COMPLETE**
**Request ID:** 69141e67405831b93d0e0f63
**Votes:** 0 | **Comments:** 0
**Completed:** 2025-11-14

**Description:**
Allow each set within an exercise to have a custom rest timer. Enables programming warm-up sets (shorter rest) and working sets (longer rest) as one workout.

**Status:** ✅ **IMPLEMENTED**
**Implementation:** Database v17, `Routine.kt`, `ExerciseEditDialog.kt`, `MainViewModel.kt`
- Database migration v16 → v17 complete (MIGRATION_16_17)
- Added `setRestSeconds: List<Int>` to RoutineExercise model
- UI shows per-set rest time pickers (10-300 seconds range)
- Workflow uses correct rest time after each completed set
- Helper methods with 60s fallback for safety

**Feasibility:** ✅ **HIGH** (Medium complexity, requires DB migration)

**Technical Analysis:**
- **Database:** Need to add `restTimeSeconds` field to `RoutineExerciseEntity`
- **Migration:** Database v16 → v17 migration required
- **UI:** Update routine builder to allow per-set rest time configuration
- **Workout Logic:** Modify rest timer logic to use set-specific rest time

**Dependencies:**
- Database migration (minor risk for beta users)

**Effort Estimate:** 1 week

**Implementation Steps:**
1. Add `restTimeSeconds: Int?` to `RoutineExerciseEntity` (nullable for backward compatibility)
2. Create MIGRATION_16_17 in AppModule.kt
3. Update routine creation UI - add "Custom Rest Time" toggle per set
4. Update `WorkoutViewModel.kt` - use set-specific rest time if set, else default
5. Update rest timer display to show "Set 1 Rest: 60s" vs "Default Rest: 90s"

**Priority Justification:**
Common request from experienced lifters. Enables better progressive overload programming. Medium complexity but high training value.

---

### 🟡 **MEDIUM PRIORITY** (Tier 2 - Weeks 7-14)

#### 4. **Add Custom Exercises to Library**
**Request ID:** 6913cae2bfd421f95b776394 + 6913f2bd42e37abbe7e4329e (duplicate)
**Votes:** 1 | **Comments:** 1

**Description:**
Allow users to create custom exercises beyond the pre-defined library.

**Feasibility:** ✅ **MEDIUM** (High complexity, significant DB/UI work)

**Technical Analysis:**
- **Database:** `ExerciseEntity` already exists, need to add `isCustom: Boolean` field
- **Migration:** Database v16 → v17 (or v18 if rest time done first)
- **UI:** Need custom exercise creation form (name, muscle group, equipment, cable config)
- **Video:** Custom exercises won't have videos - need placeholder handling
- **Sync:** Need to ensure custom exercises don't conflict with future library updates

**Dependencies:**
- Exercise library architecture (already implemented)
- Video handling for exercises without videos

**Effort Estimate:** 2-3 weeks

**Implementation Steps:**
1. Add `isCustom: Boolean` and `userId: String?` to `ExerciseEntity`
2. Create MIGRATION_X_Y for new fields
3. Create `CustomExerciseCreationScreen.kt` - form UI
4. Update `ExerciseLibraryViewModel.kt` - add custom exercise creation logic
5. Update exercise list to show custom exercises with indicator
6. Add delete/edit custom exercises
7. Handle exercises without videos (placeholder image/animation)

**Architectural Considerations:**
- Custom exercises should be user-specific (don't sync across devices unless cloud storage added)
- Need validation to prevent conflicts with official library names
- Consider export/import of custom exercises for sharing

**Priority Justification:**
Requested feature with vote support. Adds significant flexibility for users with unique equipment setups or specialized training.

---

#### 5. **Progressive Overload / Warm-up Sets**
**Request ID:** 6913caeaee97fa7ed395bfdd
**Votes:** 0 | **Comments:** 0

**Description:**
Support for warm-up sets with progressive loading (e.g., 40% → 60% → 80% → 100% working weight).

**Feasibility:** ✅ **MEDIUM** (Medium-high complexity, overlaps with #3)

**Technical Analysis:**
- **Database:** Need to add `setType: String` (warmup, working, backoff) and `weightPercentage: Float?` to sets
- **UI:** Routine builder needs set type selector and percentage calculator
- **Workout Logic:** Calculate actual weight from percentage during workout
- **Display:** Show "Warm-up Set 1: 135 lbs (60% of 225 lbs)" in workout view

**Dependencies:**
- Overlaps with #3 (per-set rest time) - should implement together
- Requires defining a "working weight" for the exercise

**Effort Estimate:** 2-3 weeks

**Implementation Steps:**
1. Add `setType: String` and `workingWeightPercentage: Float?` to routine exercise sets
2. Create MIGRATION_X_Y
3. Update routine builder UI - add set type dropdown (Warm-up, Working, Backoff)
4. Add percentage calculator UI - "This set is X% of working weight (Y lbs)"
5. Update workout logic to calculate actual weight from percentage
6. Add visual indicators for set types in workout view

**Architectural Considerations:**
- Need to determine "working weight" - is it user-defined or calculated from PR tracking?
- Percentage-based weights need rounding to nearest 0.5lb or 1lb increment

**Priority Justification:**
Useful for structured training programs. Moderate complexity. Consider combining with #3 (per-set rest) for comprehensive set customization.

---

#### 6. **Add Option for AMRAP to Number of Reps**
**Request ID:** 69141e7be853b48f037ef7c2
**Votes:** 0 | **Comments:** 0

**Description:**
Add "As Many Reps As Possible" (AMRAP) as a rep target option instead of fixed rep count.

**Feasibility:** ✅ **HIGH** (Medium complexity, requires workout logic changes)

**Technical Analysis:**
- **Database:** Add `targetReps: Int?` (nullable) and `isAMRAP: Boolean` to routine exercise sets
- **UI:** Routine builder needs AMRAP toggle
- **Workout Logic:** For AMRAP sets, no rep target - user manually ends set
- **Auto-stop:** Disable auto-stop for AMRAP sets
- **Display:** Show "AMRAP" instead of "0/10 reps"

**Dependencies:**
- None - isolated feature

**Effort Estimate:** 1 week

**Implementation Steps:**
1. Add `isAMRAP: Boolean` field to routine exercise set model
2. Create MIGRATION_X_Y
3. Update routine builder - add AMRAP toggle (hides rep count input)
4. Update workout view - show "AMRAP" badge, no rep progress bar
5. Update set completion logic - AMRAP sets end when user clicks "Complete Set"
6. Update auto-stop logic to skip AMRAP sets
7. Show final rep count in set summary: "AMRAP Set: 15 reps completed"

**Priority Justification:**
Common training methodology (especially in CrossFit, powerlifting accessories). Relatively simple to implement.

---

#### 7. **Grouping for Routines**
**Request ID:** 6913cacec753df930e03d267
**Votes:** 0 | **Comments:** 0

**Description:**
(No description provided - appears to overlap with #1 or possibly requesting routine categorization)

**Feasibility:** ⚠️ **UNCLEAR** (Insufficient detail)

**Possible Interpretations:**
1. **Workout History Grouping** - Same as Request #1 (already covered)
2. **Routine Organization** - Folder/category system for organizing routines
3. **Superset/Circuit Grouping** - Group exercises to be performed back-to-back

**Recommended Action:**
Request clarification from user. Most likely this is either:
- Duplicate of #1 (routine history grouping) - already prioritized
- Routine library organization (folders/categories) - medium priority
- Superset support (group exercises for circuits) - high priority if confirmed

**Effort Estimate:** TBD pending clarification

**Priority Justification:**
Cannot prioritize without clarification. Recommend contacting user for details.

---

#### 8. **Syncing with Android Health Connect**
**Request ID:** 6913f1d3b514085b60a04437
**Votes:** 0 | **Comments:** 0

**Description:**
Integrate with Android Health Connect to sync workout data with other fitness apps.

**Feasibility:** ✅ **MEDIUM** (High complexity, external API integration)

**Technical Analysis:**
- **API:** Android Health Connect API (requires API 34+ / Android 14+)
- **Permissions:** Need to request Health Connect permissions
- **Data Mapping:** Map workout data to Health Connect schema
  - Exercise sessions → ExerciseSession
  - Reps → ExerciseRepetitions
  - Sets → Exercise sets
  - Calories → ActiveCaloriesBurned
- **Sync Logic:** Bidirectional sync or write-only?

**Dependencies:**
- Requires Android 14+ (API 34) - current min API is 26
- May need to make Health Connect optional for older devices

**Effort Estimate:** 3-4 weeks

**Implementation Steps:**
1. Add Health Connect dependency to build.gradle
2. Create `HealthConnectManager.kt` - wrapper for Health Connect API
3. Add permissions request flow
4. Create data mapping layer - Workout → ExerciseSession
5. Implement background sync worker (WorkManager)
6. Add settings toggle for Health Connect sync
7. Handle sync conflicts and errors gracefully
8. Test with popular fitness apps (Google Fit, MyFitnessPal, etc.)

**Architectural Considerations:**
- Health Connect is only available on Android 14+
- Need graceful degradation for older Android versions
- Sync should be optional and configurable
- Privacy considerations - user must explicitly enable

**Priority Justification:**
Good ecosystem integration. Moderate priority as it's a "nice-to-have" feature, not core functionality. High implementation cost.

---

### 🟢 **LOW PRIORITY** (Tier 3 - Weeks 15+)

#### 9. **Gamification - Add Badges and Streaks**
**Request ID:** 6913d6207fcb06f6850146d9
**Votes:** 0 | **Comments:** 0

**Description:**
Add achievement badges and workout streaks to encourage consistency.

**Feasibility:** ✅ **MEDIUM** (Low technical complexity, high design effort)

**Technical Analysis:**
- **Database:** Need `AchievementEntity` table with earned badges and timestamps
- **Logic:** Track workout streaks (daily, weekly), milestones (100 workouts, 1000 reps, etc.)
- **UI:** Badge display on profile, notification when earned, streak counter
- **Design:** Need to design badge icons and achievement criteria

**Effort Estimate:** 3-4 weeks (mostly design/content creation)

**Implementation Steps:**
1. Define achievement criteria (streaks, milestones, PRs, etc.)
2. Design badge icons (or use free icon library)
3. Create `AchievementEntity` and `BadgeEntity` tables
4. Create MIGRATION_X_Y
5. Implement achievement tracking logic
6. Add badge display to user profile
7. Add notification when badge earned
8. Create streak tracking (daily workout count)

**Priority Justification:**
Low priority - "nice-to-have" feature. Doesn't add core functionality. Consider for post-1.0 release.

---

#### 10. **Import Programs from Other Apps**
**Request ID:** 69141ef980142496b189cf4f
**Votes:** 0 | **Comments:** 0

**Description:**
Import workout programs from TrainHeroic, SugarWOD, Garmin, or via screenshot with AI parsing.

**Feasibility:** ⚠️ **LOW-MEDIUM** (Very high complexity, external dependencies)

**Technical Analysis:**
- **External APIs:** Most apps don't provide public APIs for program export
- **File Formats:** Would need to debug proprietary formats
- **Screenshot OCR:** Requires AI/ML model for text extraction and parsing
- **Validation:** Complex validation to ensure parsed data is correct

**Dependencies:**
- Access to external app data formats (may not be available)
- AI/ML integration for screenshot parsing (complex, expensive)
- User authentication for third-party apps

**Effort Estimate:** 8-12 weeks (very complex)

**Implementation Challenges:**
- **TrainHeroic, SugarWOD:** No public APIs - would need to scrape or debug
- **Garmin:** May have API, but requires developer partnership
- **Screenshot parsing:** Requires OCR + NLP to extract exercise names, sets, reps, weights
- **Data validation:** High risk of parsing errors leading to incorrect workouts

**Alternative Approach:**
- **Manual CSV/JSON import:** Allow users to manually export programs as CSV/JSON and import
- **Copy/paste format:** Define a simple text format users can copy/paste
- **Example:**
  ```
  Bench Press: 3x8 @ 185 lbs
  Squat: 4x6 @ 225 lbs
  Deadlift: 3x5 @ 275 lbs
  ```

**Priority Justification:**
Very low priority. Extremely high complexity with uncertain value. Recommend deferring indefinitely or implementing simple CSV import as alternative.

---

## Recommended Implementation Roadmap

### **Phase 1: Critical UX Fixes** (Weeks 1-6)

**Goal:** Fix existing issues and add quick-win features

| Request | Effort | Priority | Justification |
|---------|--------|----------|---------------|
| Routines in Workout History (#1) | 1-2 weeks | HIGH | Critical UX bug, schema already prepared |
| Smaller Weight Increments (#2) | 1-3 days | HIGH | Quick win, high user value |
| Per-Set Rest Time (#3) | 1 week | HIGH | Common training methodology |

**Deliverables:**
- ✅ Routine workout history grouping
- ✅ 0.5lb weight increments
- ✅ Per-set rest time configuration

**Estimated Total:** 3-4 weeks

---

### **Phase 2: Advanced Training Features** (Weeks 7-14)

**Goal:** Add features for serious trainers

| Request | Effort | Priority | Justification |
|---------|--------|----------|---------------|
| Custom Exercises (#4) | 2-3 weeks | MEDIUM | Flexibility for unique setups |
| Progressive Overload/Warm-up (#5) | 2-3 weeks | MEDIUM | Structured training support |
| AMRAP Sets (#6) | 1 week | MEDIUM | Common training style |
| Grouping for Routines (#7) | TBD | MEDIUM | Pending clarification |

**Deliverables:**
- ✅ Custom exercise creation
- ✅ Warm-up set programming with percentages
- ✅ AMRAP set option
- ⚠️ Routine grouping (clarify with user first)

**Estimated Total:** 6-8 weeks

---

### **Phase 3: Ecosystem Integration** (Weeks 15-18)

**Goal:** Integrate with external platforms

| Request | Effort | Priority | Justification |
|---------|--------|----------|---------------|
| Android Health Connect (#8) | 3-4 weeks | MEDIUM | Ecosystem integration |

**Deliverables:**
- ✅ Health Connect integration (Android 14+)
- ✅ Automatic workout sync

**Estimated Total:** 3-4 weeks

---

### **Phase 4: Engagement Features** (Post-1.0)

**Goal:** Gamification and retention

| Request | Effort | Priority | Justification |
|---------|--------|----------|---------------|
| Badges and Streaks (#9) | 3-4 weeks | LOW | Nice-to-have, not core |

**Deliverables:**
- ✅ Achievement system
- ✅ Workout streaks
- ✅ Badge notifications

**Estimated Total:** 3-4 weeks

---

### **Phase 5: Deferred / Out of Scope**

**Requests to defer or reject:**

| Request | Reason |
|---------|--------|
| Import from Other Apps (#10) | Extremely high complexity, uncertain ROI, no public APIs |

**Recommendation:**
Implement simple CSV/JSON import instead as alternative.

---

## Database Migration Plan

**Current Version:** 16

**Planned Migrations:**

1. **v16 → v17:** Per-set rest time
   - Add `restTimeSeconds: Int?` to RoutineExerciseEntity
   - Backward compatible (nullable field)

2. **v17 → v18:** Custom exercises
   - Add `isCustom: Boolean` and `createdByUserId: String?` to ExerciseEntity
   - Add default value for existing exercises

3. **v18 → v19:** Progressive overload / AMRAP
   - Add `setType: String`, `workingWeightPercentage: Float?`, `isAMRAP: Boolean` to sets
   - Add default values for backward compatibility

4. **v19 → v20:** Achievements (if implemented)
   - Add `AchievementEntity` and `BadgeEntity` tables
   - No changes to existing tables

**Migration Risk:** Low (all migrations add nullable fields or new tables, no data loss)

---

## Technical Dependencies

### Required for Phase 1-2:
- ✅ Room Database (already implemented)
- ✅ Routine/Exercise architecture (already implemented)
- ✅ BLE Protocol (verify 0.5lb support)

### Required for Phase 3:
- ⚠️ Android Health Connect API (API 34+)
- ⚠️ Min SDK bump to 34 (or conditional feature gating)

### Required for Phase 4:
- ⚠️ Badge icon design (assets needed)
- ⚠️ Achievement criteria definition (product decision)

### Required for Phase 5 (Deferred):
- ❌ Third-party app APIs (not available)
- ❌ OCR/AI parsing (very complex)

---

## Risk Assessment

### **Low Risk Features** (Safe to implement):
- ✅ Smaller weight increments (#2)
- ✅ Per-set rest time (#3)
- ✅ AMRAP sets (#6)

### **Medium Risk Features** (Requires careful testing):
- ⚠️ Routine history grouping (#1) - UI complexity
- ⚠️ Custom exercises (#4) - Database migration
- ⚠️ Progressive overload (#5) - Calculation logic

### **High Risk Features** (Significant complexity):
- 🔴 Health Connect (#8) - External API, Android version dependency
- 🔴 Program import (#10) - Extremely complex, many failure points

---

## Cost-Benefit Analysis

| Feature | User Value | Dev Effort | ROI | Recommendation |
|---------|-----------|-----------|-----|----------------|
| Routine History (#1) | HIGH | MEDIUM | ⭐⭐⭐⭐⭐ | **IMPLEMENT ASAP** |
| Weight Increments (#2) | HIGH | LOW | ⭐⭐⭐⭐⭐ | **IMPLEMENT ASAP** |
| Per-Set Rest (#3) | HIGH | LOW | ⭐⭐⭐⭐⭐ | **IMPLEMENT ASAP** |
| Custom Exercises (#4) | MEDIUM | HIGH | ⭐⭐⭐ | Implement Phase 2 |
| Progressive Overload (#5) | MEDIUM | HIGH | ⭐⭐⭐ | Implement Phase 2 |
| AMRAP (#6) | MEDIUM | MEDIUM | ⭐⭐⭐⭐ | Implement Phase 2 |
| Routine Grouping (#7) | TBD | TBD | TBD | Clarify first |
| Health Connect (#8) | MEDIUM | HIGH | ⭐⭐ | Implement Phase 3 |
| Badges/Streaks (#9) | LOW | MEDIUM | ⭐⭐ | Post-1.0 |
| Program Import (#10) | LOW | VERY HIGH | ⭐ | **DEFER** |

**Legend:**
⭐⭐⭐⭐⭐ = Excellent ROI (high value, low effort)
⭐⭐⭐⭐ = Good ROI
⭐⭐⭐ = Acceptable ROI
⭐⭐ = Marginal ROI
⭐ = Poor ROI (defer or reject)

---

## Next Steps

1. **Clarify Request #7** (Grouping for Routines)
   - Contact user for details
   - Determine if duplicate of #1 or new feature

2. **Verify 0.5lb Support** (Request #2)
   - Test with Vitruvian hardware
   - Check BLE protocol specification
   - If not supported, document limitation

3. **Prioritize Phase 1 Implementation** (Weeks 1-6)
   - Start with Request #1 (Routine History) - critical UX issue
   - Follow with Request #2 (Weight Increments) - quick win
   - Complete with Request #3 (Per-Set Rest) - training feature

4. **User Feedback Loop**
   - After Phase 1 completion, gather user feedback
   - Re-prioritize Phase 2 based on usage data
   - Consider adding telemetry to understand feature usage

---

## Conclusion

**Total Requests:** 11 (10 unique, 1 duplicate)
**High Priority:** 3 requests (Weeks 1-6)
**Medium Priority:** 5 requests (Weeks 7-18)
**Low Priority:** 2 requests (Post-1.0)
**Deferred:** 1 request (Too complex)

**Recommended Focus:** Phase 1 (Weeks 1-6) - Critical UX fixes and quick wins

**Total Timeline:** 18-24 weeks for Phases 1-3, post-1.0 for Phase 4

---

**Document Version:** 1.0
**Last Updated:** 2025-11-13
**Author:** Claude Code (AI Analysis)
