package fr.cnrs.lacito.liftdsl.parser;

import fr.cnrs.lacito.liftdsl.model.*;
import java.util.*;

/**
 * Renders the normalized command model as reference Lift-DSL clauses.
 *
 * <p>The renderer is intentionally deterministic so concise commands can be
 * inspected, logged, or passed to another reference-syntax consumer.</p>
 */
public final class ReferenceRenderer {
    /**
     * Renders all commands in source order.
     *
     * @param program parsed program
     * @return reference Lift-DSL text
     * @throws NullPointerException if {@code program} is null
     */
    public String render(Program program) {
        StringBuilder out=new StringBuilder();
        for(Command c:program.commands()){if(out.length()>0)out.append('\n');render(c,out,0);}
        return out.toString();
    }
    private void render(Command c,StringBuilder out,int depth){
        indent(out,depth);
        if(c.kind()==Command.Kind.LANGUAGE_DEFAULT){out.append("language-default ").append(c.languageSet().orElseThrow()).append(" = \"").append(escape(c.language().orElse(""))).append("\"");return;}
        if(c.kind()==Command.Kind.SET||c.kind()==Command.Kind.UPDATE){PropertyValue p=c.property().orElseThrow();out.append(c.kind().name().toLowerCase()).append(' ').append(property(p)).append(" = \"").append(escape(p.value())).append("\" on ").append(ref(c.target()));}
        else if(c.kind()==Command.Kind.CLEAR){out.append("clear ").append(property(c.property().orElseThrow())).append(" on ").append(ref(c.target()));}
        else if(c.kind()==Command.Kind.DELETE){out.append("delete ").append(c.componentType()).append(selector(c.target().selector()));if(c.target().parent().isPresent())out.append(" under ").append(ref(c.target().parent().get()));}
        else {out.append(c.kind().name().toLowerCase()).append(' ').append(c.componentType());if(!c.values().isEmpty()){out.append('(');for(int i=0;i<c.values().size();i++){if(i>0)out.append(", ");PropertyValue p=c.values().get(i);out.append(property(p)).append(" = \"").append(escape(p.value())).append("\"");}out.append(')');}if(c.target()!=null)out.append(" under ").append(ref(c.target()));}
        for(Command child:c.children()){out.append('\n');render(child,out,depth+1);}
    }
    private String ref(ComponentRef r){StringBuilder b=new StringBuilder(r.type()).append(selector(r.selector()));if(r.parent().isPresent())b.append(" of ").append(ref(r.parent().get()));return b.toString();}
    private String selector(Selector s){if(s.index().isPresent())return "[index = "+s.index().getAsInt()+"]";if(s.predicates().isEmpty())return "[]";StringBuilder b=new StringBuilder("[");int i=0;for(Map.Entry<String,String>e:s.predicates().entrySet()){if(i++>0)b.append(", ");b.append(e.getKey()).append(" = \"").append(escape(e.getValue())).append("\"");}return b.append(']').toString();}
    private String property(PropertyValue p){return p.name()+(p.language()==null?"":"@"+p.language())+(p.type()==null?"":"^"+p.type());}
    private String escape(String s){return s==null?"":s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n");}
    private void indent(StringBuilder b,int n){for(int i=0;i<n;i++)b.append("  ");}
}
