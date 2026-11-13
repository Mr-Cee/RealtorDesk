/** Separate optional ISO dates; blank milestones are stored as SQL NULL for reporting. */
record KeyDates(String start,String offer,String close) {
    static final KeyDates EMPTY=new KeyDates("","","");
    KeyDates {start=normalize(start);offer=normalize(offer);close=normalize(close);}
    private static String normalize(String value){return value==null||value.isBlank()?"":java.time.LocalDate.parse(value.trim()).toString();}
}
