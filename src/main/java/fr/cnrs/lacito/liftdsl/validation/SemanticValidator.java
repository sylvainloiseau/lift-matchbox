package fr.cnrs.lacito.liftdsl.validation;
import fr.cnrs.lacito.liftdsl.model.*;
import java.util.*;

/**
 * Validates command structure independently of dictionary contents.
 *
 * <p>This pass checks vocabulary, legal parent-child relationships, selector
 * shape, duplicate initializers, and required creation properties.</p>
 */
public final class SemanticValidator {
    private static final Set<String> COMPONENTS=Set.of("entry","sense","example","etymology","variant","relation","illustration","media","pronunciation","reversal","trait","annotation","note","field","translation");
    private static final Set<String> PROPERTIES=Set.of("form","morpheme","definition","gloss","category","text","source","target","url","label","transcription","type","value","comment","when","who");
    private static final Map<String,Set<String>> PARENTS=Map.ofEntries(
        Map.entry("entry",Set.<String>of()), Map.entry("sense",Set.of("entry","sense")), Map.entry("example",Set.of("sense")),
        Map.entry("translation",Set.of("example")), Map.entry("pronunciation",Set.of("entry","variant")),
        Map.entry("variant",Set.of("entry")), Map.entry("relation",Set.of("entry","sense","variant","reversal")),
        Map.entry("etymology",Set.of("entry")), Map.entry("illustration",Set.of("sense")), Map.entry("reversal",Set.of("entry","sense")),
        Map.entry("trait",Set.of("entry","sense","example","variant","relation","etymology","pronunciation","reversal","illustration","media","annotation","note","field","translation")),
        Map.entry("annotation",Set.of("entry","sense","example","variant","relation","etymology","pronunciation","reversal","illustration","media","trait","note","field","translation")),
        Map.entry("note",Set.of("entry","sense")), Map.entry("field",Set.of("entry","sense","example","variant","relation","etymology","pronunciation","reversal","note","field")));
    /**
     * Validates every command in a program in source order.
     *
     * @param program program to validate
     * @throws NullPointerException if {@code program} is null
     * @throws ValidationError if a semantic rule is violated
     */
    public void validate(Program program) {
        for(Command c:program.commands()) validate(c);
    }
    private void validate(Command c) {
        if(c.componentType()!=null && !COMPONENTS.contains(c.componentType())) throw new ValidationError(ValidationError.Code.UNKNOWN_COMPONENT,c.componentType());
        c.property().ifPresent(p->{if(!PROPERTIES.contains(p.name())) throw new ValidationError(ValidationError.Code.UNKNOWN_PROPERTY,p.name());});
        if(c.target()!=null) validateRef(c.target(), (c.kind()==Command.Kind.CREATE||c.kind()==Command.Kind.UPSERT||c.kind()==Command.Kind.ENSURE) ? c.componentType() : null);
        if((c.kind()==Command.Kind.CREATE||c.kind()==Command.Kind.UPSERT||c.kind()==Command.Kind.ENSURE) && c.componentType()!=null) {
            Set<String> seen=new HashSet<>(); for(PropertyValue p:c.values()) if(!seen.add(p.name())) throw new ValidationError(ValidationError.Code.DUPLICATE_PROPERTY,p.name());
            if(c.kind()==Command.Kind.CREATE && "entry".equals(c.componentType()) && c.values().stream().noneMatch(p->p.name().equals("form")))
                throw new ValidationError(ValidationError.Code.MISSING_REQUIRED_PROPERTY,"entry requires form");
            if((c.kind()==Command.Kind.CREATE || c.kind()==Command.Kind.UPSERT || c.kind()==Command.Kind.ENSURE) && "sense".equals(c.componentType()) && c.values().stream().noneMatch(p->p.name().equals("gloss")))
                throw new ValidationError(ValidationError.Code.MISSING_REQUIRED_PROPERTY,"sense requires gloss");
            if((c.kind()==Command.Kind.CREATE || c.kind()==Command.Kind.UPSERT || c.kind()==Command.Kind.ENSURE) && "example".equals(c.componentType()) && c.values().stream().noneMatch(p->p.name().equals("text")))
                throw new ValidationError(ValidationError.Code.MISSING_REQUIRED_PROPERTY,"example requires text");
        }
        for(Command child:c.children()) validate(child);
    }
    private void validateRef(ComponentRef ref, String child) {
        if(!COMPONENTS.contains(ref.type())) throw new ValidationError(ValidationError.Code.UNKNOWN_COMPONENT,ref.type());
        if(child!=null && !PARENTS.getOrDefault(child,Set.of()).contains(ref.type()))
            throw new ValidationError(ValidationError.Code.ILLEGAL_PARENT,ref.type()+" cannot contain "+child);
        if(ref.selector().isEmpty() && !"entry".equals(ref.type())) throw new ValidationError(ValidationError.Code.INVALID_SELECTOR,"selector required for "+ref.type());
        ref.parent().ifPresent(p->validateRef(p,ref.type()));
    }
}
