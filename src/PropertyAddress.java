import java.util.*;

record PropertyAddress(String number,String direction,String street,String type,String unit,String city,String state,String zip) {
    PropertyAddress {number=clean(number);direction=clean(direction);street=clean(street);type=clean(type);unit=clean(unit);city=clean(city);state=clean(state).toUpperCase(Locale.ROOT);zip=clean(zip);}
    static String clean(String s){return s==null?"":s.trim().replaceAll("\\s+"," ");}
    void validate(){if(number.isEmpty()||street.isEmpty()||city.isEmpty()||state.isEmpty()||zip.isEmpty())throw new IllegalArgumentException("Street number, street name, city, state and ZIP are required.");}
    String formatted(){String road=String.join(" ",java.util.List.of(number,direction,street,type).stream().filter(s->!s.isEmpty()).toList());if(!unit.isEmpty())road+=" Unit "+unit;return road+", "+city+", "+state+" "+zip;}
    static final Map<String,String> TYPES=Map.ofEntries(Map.entry("street","st"),Map.entry("avenue","ave"),Map.entry("road","rd"),Map.entry("drive","dr"),Map.entry("lane","ln"),Map.entry("court","ct"),Map.entry("boulevard","blvd"),Map.entry("place","pl"),Map.entry("circle","cir"),Map.entry("highway","hwy"),Map.entry("terrace","ter"),Map.entry("parkway","pkwy"),Map.entry("trail","trl"));
    static final Map<String,String> DIRECTIONS=Map.of("north","n","south","s","east","e","west","w","northeast","ne","northwest","nw","southeast","se","southwest","sw");
    static String norm(String s){return ClientSearch.normalize(clean(s)).replaceAll("[.,]","");}
    String key(){return String.join("|",norm(number),DIRECTIONS.getOrDefault(norm(direction),norm(direction)),norm(street),TYPES.getOrDefault(norm(type),norm(type)),norm(unit).replaceFirst("^(unit|apt|suite|#)\\s*",""),norm(city),norm(state),norm(zip).split("-",2)[0]);}
    boolean complete(){return !number.isEmpty()&&!street.isEmpty()&&!city.isEmpty()&&!state.isEmpty()&&!zip.isEmpty();}
    /** Conservative prefill: original full address remains available for review. */
    static PropertyAddress legacy(String full) {
        String[] sections=clean(full).split(",");String number="",direction="",street=sections[0],type="",city="",state="",zip="",unit="";
        var words=new ArrayList<>(Arrays.asList(street.split(" ")));
        if(!words.isEmpty()&&words.get(0).matches("[0-9].*"))number=words.remove(0);
        if(!words.isEmpty()&&(DIRECTIONS.containsKey(norm(words.get(0)))||DIRECTIONS.containsValue(norm(words.get(0)))))direction=words.remove(0);
        for(int i=0;i<words.size();i++)if(words.get(i).matches("(?i)unit|apt|suite|#")){unit=String.join(" ",words.subList(i+1,words.size()));words=new ArrayList<>(words.subList(0,i));break;}
        if(!words.isEmpty()){String last=norm(words.get(words.size()-1));if(TYPES.containsKey(last)||TYPES.containsValue(last))type=words.remove(words.size()-1);}
        street=String.join(" ",words);
        if(sections.length==3){city=sections[1].trim();String[] tail=sections[2].trim().split("\\s+");if(tail.length>=2&&tail[tail.length-1].matches("[0-9]{5}(-[0-9]{4})?")){zip=tail[tail.length-1];state=String.join(" ",Arrays.copyOf(tail,tail.length-1));}}
        return new PropertyAddress(number,direction,street,type,unit,city,state,zip);
    }
}
