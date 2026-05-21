package com.haru.schedule.api.dto;

import com.haru.schedule.domain.TutorScheduleSlot;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record TutorScheduleResponse(
        List<ScheduleSlotResponse> slots
) {

    public static TutorScheduleResponse from(List<TutorScheduleSlot> slots, Collection<Long> bookedSlotIds) {
        Set<Long> bookedSlotIdSet = bookedSlotIds == null ? Set.of() : new HashSet<>(bookedSlotIds);
        return new TutorScheduleResponse(
                slots.stream()
                        .map(slot -> ScheduleSlotResponse.from(slot, bookedSlotIdSet.contains(slot.getId())))
                        .toList()
        );
    }
}
