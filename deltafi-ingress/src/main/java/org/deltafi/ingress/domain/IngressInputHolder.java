package org.deltafi.ingress.domain;

import io.minio.messages.Event;
import org.deltafi.dgs.generated.types.ObjectReferenceInput;
import org.deltafi.dgs.generated.types.SourceInfoInput;

import java.util.Objects;

public class IngressInputHolder {

    Event event;
    SourceInfoInput sourceInfoInput;
    ObjectReferenceInput objectReferenceInput;

    public IngressInputHolder(Event event) {
        this.event = event;
    }

    public IngressInputHolder(Event event, SourceInfoInput sourceInfoInput, ObjectReferenceInput objectReferenceInput) {
        this.event = event;
        this.sourceInfoInput = sourceInfoInput;
        this.objectReferenceInput = objectReferenceInput;
    }

    public Event getEvent() {
        return event;
    }

    public void setEvent(Event event) {
        this.event = event;
    }

    public SourceInfoInput getSourceInfoInput() {
        return sourceInfoInput;
    }

    public void setSourceInfoInput(SourceInfoInput sourceInfoInput) {
        this.sourceInfoInput = sourceInfoInput;
    }

    public ObjectReferenceInput getObjectReferenceInput() {
        return objectReferenceInput;
    }

    public void setObjectReferenceInput(ObjectReferenceInput objectReferenceInput) {
        this.objectReferenceInput = objectReferenceInput;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IngressInputHolder ingressInputHolder = (IngressInputHolder) o;
        return Objects.equals(event, ingressInputHolder.event) && Objects.equals(sourceInfoInput, ingressInputHolder.sourceInfoInput) && Objects.equals(objectReferenceInput, ingressInputHolder.objectReferenceInput);
    }

    @Override
    public int hashCode() {
        return Objects.hash(event, sourceInfoInput, objectReferenceInput);
    }
}
