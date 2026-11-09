package za.co.talentsearch.candidate.application;
import java.io.IOException;
import java.time.YearMonth;
import java.util.*;
import java.util.regex.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import za.co.talentsearch.candidate.domain.*;
@Component
public class CvParser {
 private final List<String> skills;
 private static final Pattern JOB=Pattern.compile("^Experience: (.+?) \\| (.+?) \\| (\\d{4}-\\d{2}) \\| (\\d{4}-\\d{2}|PRESENT)$");
 public CvParser(@Value("${app.cv.skills}") List<String> skills){this.skills=List.copyOf(skills);}
 public CandidateProfile parse(SourceCandidate source){
  if(source.cvDocument()==null || source.cvDocument().length==0)throw new IllegalArgumentException("MISSING_CV");
  if(source.cvDocument().length>10*1024*1024)throw new IllegalArgumentException("CV_TOO_LARGE");
  if(!"application/pdf".equalsIgnoreCase(source.cvContentType()))throw new IllegalArgumentException("UNSUPPORTED_CV_TYPE");
  String text;
  try(var pdf=Loader.loadPDF(source.cvDocument())){
   if(pdf.isEncrypted() || !pdf.getCurrentAccessPermission().canExtractContent())throw new IllegalArgumentException("PDF_EXTRACTION_FORBIDDEN");
   text=new PDFTextStripper().getText(pdf);
  }catch(IOException e){throw new IllegalArgumentException("INVALID_PDF",e);}
  text=text.replace('\u00a0',' ').replace("\r\n","\n").replace('\r','\n');
  var lines=text.lines().map(l->l.replaceAll("[\\t ]+"," ").trim()).filter(l->!l.isBlank()).toList();
  if(lines.isEmpty())throw new IllegalArgumentException("NO_EXTRACTABLE_TEXT");
  String normalized=String.join("\n",lines);
  var found=skills.stream().filter(s->Pattern.compile("(?i)(?<![\\p{Alnum}])"+Pattern.quote(s)+"(?![\\p{Alnum}])").matcher(normalized).find()).distinct().toList();
  var experience=new ArrayList<CandidateProfile.Experience>();
  var periods=new TreeSet<YearMonth>();
  boolean uncertainDates=false;
  for(String line:lines){
   var m=JOB.matcher(line);
   if(m.matches()){
    try{
     var start=YearMonth.parse(m.group(3));
     var end="PRESENT".equals(m.group(4))?YearMonth.now():YearMonth.parse(m.group(4));
     if(end.isBefore(start)||start.isBefore(YearMonth.of(1900,1))||end.isAfter(YearMonth.now()))throw new IllegalArgumentException();
     experience.add(new CandidateProfile.Experience(m.group(1),m.group(2),m.group(3),m.group(4)));
     for(var month=start;!month.isAfter(end);month=month.plusMonths(1))periods.add(month);
    }catch(RuntimeException e){uncertainDates=true;}
   }else if(line.startsWith("Experience:"))uncertainDates=true;
  }
  var education=values(lines,"Education: ");var certifications=values(lines,"Certification: ");
  Integer years=experience.isEmpty()||uncertainDates?null:periods.size()/12;
  String role=experience.isEmpty()?"UNKNOWN":experience.getFirst().jobTitle();
  String status=years==null||found.isEmpty()||education.contains("UNKNOWN")||certifications.contains("UNKNOWN")?"PARTIAL":"PARSED";
  return new CandidateProfile(source.candidateNumber(),source.name(),source.surname(),source.email(),found,years,role,status,experience,education,certifications);
 }
 private List<String> values(List<String> lines,String prefix){
  var values=lines.stream().filter(l->l.startsWith(prefix)).map(l->l.substring(prefix.length()).trim()).filter(l->!l.isEmpty()).toList();
  return values.isEmpty()?List.of("UNKNOWN"):values;
 }
}
