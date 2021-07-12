package org.deltafi.ingress.domain;

import io.minio.messages.Event;
import org.deltafi.dgs.generated.types.IngressInput;
import org.deltafi.dgs.generated.types.ObjectReferenceInput;
import org.deltafi.dgs.generated.types.SourceInfoInput;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

public class IngressInputHolder {

    Event event;
    IngressInput ingressInput;

    @SuppressWarnings("CdiInjectionPointsInspection")
    public IngressInputHolder(Event event) {
        this.event = event;
    }

    public IngressInputHolder(Event event, SourceInfoInput sourceInfoInput, ObjectReferenceInput objectReferenceInput) {
        this.event = event;
        this.ingressInput = IngressInput.newBuilder()
                .did(UUID.randomUUID().toString())
                .sourceInfo(sourceInfoInput)
                .objectReference(objectReferenceInput)
                .created(Objects.nonNull(event.eventTime()) ? event.eventTime().toOffsetDateTime() : OffsetDateTime.now())
                .build();
    }

    public Event getEvent() {
        return event;
    }

    @SuppressWarnings("unused")
    public void setEvent(Event event) {
        this.event = event;
    }

    public IngressInput getIngressInput() {
        return ingressInput;
    }

    @SuppressWarnings("unused")
    public void setIngressInput(IngressInput ingressInput) {
        this.ingressInput = ingressInput;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IngressInputHolder ingressInputHolder = (IngressInputHolder) o;
        return Objects.equals(event, ingressInputHolder.event) && Objects.equals(ingressInput, ingressInputHolder.ingressInput);
    }

    @Override
    public int hashCode() {
        return Objects.hash(event, ingressInput);
    }
}
