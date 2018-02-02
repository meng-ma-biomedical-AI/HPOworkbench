package org.monarchinitiative.hpoworkbench.smallfile;

import com.github.phenomics.ontolib.formats.hpo.HpoFrequency;
import com.github.phenomics.ontolib.formats.hpo.HpoOntology;
import com.github.phenomics.ontolib.formats.hpo.HpoTerm;
import com.github.phenomics.ontolib.formats.hpo.HpoTermRelation;
import com.github.phenomics.ontolib.graph.data.Edge;
import com.github.phenomics.ontolib.ontology.data.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.monarchinitiative.hpoworkbench.exception.HPOException;
import org.monarchinitiative.hpoworkbench.util.DateUtil;

import java.util.*;
import java.util.stream.Collectors;

import static org.monarchinitiative.hpoworkbench.smallfile.DiseaseDatabase.DECIPHER;
import static org.monarchinitiative.hpoworkbench.smallfile.DiseaseDatabase.OMIM;
import static org.monarchinitiative.hpoworkbench.smallfile.DiseaseDatabase.ORPHANET;
import static org.monarchinitiative.hpoworkbench.smallfile.SmallFileQCCode.*;
import static org.monarchinitiative.hpoworkbench.util.DateUtil.convertToCanonicalDateFormat;

/**
 * Created by peter on 1/20/2018.
 * This class is intended to take data from a single line of an "old" small file entry. Its main purpose os to map and
 * transform the data to the new field formats so that we can transform it into a {@link V2SmallFileEntry} object.
 * The transformations performed are
 * <ol>
 *     <li>Annotations to alt_id's are replaced wuth the current primary ud </li>
 *     <li>The data format is unifed to YYYY-MM-DD</li>
 *     <li>The fields geneId, geneName, genotype, and genesymbol are removed</li>
 *     <li>A new modifier field is added. If possible, the free text in the Description field is used to put something
 *     into the modifier field</li>
 *     <li>The fields entityName,qualityId ,qualityName , addlEntityName, and addlEntityId are removed</li>
 *     <li>The fields abnormalId and abnomralname are removed.</li>
 *     <li>The field orthologs is removed</li>
 *     <li>The current old file format has a single field for frequency. We try to make three separate fields from this
 *     TODO discuss with Seb</li>
 *     <li>The fields evidenceID, evidenceName, and evidence are reduced to one field "evidence" that is allow to have one
 *     of four codes, IEA, ICE, TAS, PCS only.</li>
 * </ol>
 * @author Peter Robinson
 */
public class OldSmallFileEntry {
    private static final Logger LOGGER = LogManager.getLogger();
    private DiseaseDatabase database = null;
    private String diseaseID = null;
    private String diseaseName = null;
    /** gene ID. We will delete this field for the new version*/
    private String geneID = null;
    /** Gene symbol. We will delete this field for the new version */
    private String geneName = null;
    /** We will delete the genotype field in the new version*/
    private String genotype = null;
    /** We will delete the gene symbol in the new version*/
    private String genesymbol = null;
    /** THe HPO id */
    private TermId phenotypeId = null;
    /** THe HPO label */
    private String phenotypeName = null;
    /** THis should be an HPO Id */
    private TermId ageOfOnsetId = null;
    /** Corresponding Name of HPO age of onset term. */
    private String ageOfOnsetName = null;

    private String evidenceID = null;

    private String evidenceName = null;

    private String frequencyString = null;

    private TermId frequencyId = null;

    // Frequency Ids
    private static final TermPrefix HP_PREFIX = new ImmutableTermPrefix("HP");
    private static final TermId FrequencyRoot = new ImmutableTermId(HP_PREFIX, "0040279");
    private static final TermId FREQUENT = HpoFrequency.FREQUENT.toTermId();
    private static final TermId VERY_FREQUENT = HpoFrequency.VERY_FREQUENT.toTermId();
    private static final TermId OBLIGATE = new ImmutableTermId(HP_PREFIX, "0040280");
    private static final TermId OCCASIONAL = HpoFrequency.OCCASIONAL.toTermId();
    private static final TermId EXCLUDED = HpoFrequency.EXCLUDED.toTermId();
    private static final TermId VERY_RARE = HpoFrequency.VERY_RARE.toTermId();
    /**
     * If present, a limitation to MALE or FEMALE.
     */
    private String sexID = null;
    /** Redundant with {@link #sexID}. */
    private String sexName = null;
    /** A previous verion of HPOA did not have the {@link #sexID} field but this shoudl also be MALE or FEMALE. */
    private String sex = null;

    private final static String MALE_CODE = "Male";
    private final static String FEMALE_CODE = "Female";
    /** If present, "NOT" */
    private String negationID = null;
    /** Redundant with {@link #negationID}. */
    private String negationName = null;
    /** Free text, b ut may contain things we can turn into modifiers. */
    private String description = null;
    /** This was not present in the old small file but will be created here if possible from the Description field. */
    private Set<TermId> modifierset = new HashSet<>();
    /** The source of the assertion, often a string such as PMID:123 or OMIM:100123 */
    private String pub = null;
    /** The biocurator */
    private String assignedBy = null;
    /* The date the annotation was first created. */
    private String dateCreated = null;
    /** Added here for completeness. But we will be discarding this field in the v2 because it was hardly ever used. */
    private String entityId = null;
    /** Added here for completeness. But we will be discarding this field in the v2 because it was hardly ever used. */
    private String entityName = null;
    /** Added here for completeness. But we will be discarding this field in the v2 because it was hardly ever used. */
    private String qualityId = null;
    /** Added here for completeness. But we will be discarding this field in the v2 because it was hardly ever used. */
    private String qualityName = null;
    /** Added here for completeness. But we will be discarding this field in the v2 because it was hardly ever used. */
    private String addlEntityName = null;
    /** Added here for completeness. But we will be discarding this field in the v2 because it was hardly ever used. */
    private String addlEntityId = null;
    /**
     * Some entries have just evidence rather than evidenceId and evidenceName. We do the best we can to get one evidence code but
     * looking at all three fields one after the other, with evidenceId being prefered, then evidenceName, then evidence
     */
    private String evidence = null;
    /** Added here for completeness. But we will be discarding this field in the v2 because it was hardly ever used.*/
     private String abnormalId = null;
    /** Added here for completeness. But we will be discarding this field in the v2 because it was hardly ever used. */
    private String abnormalName = null;
    private String othologs = null;

    private static HpoOntology ontology = null;
    private static Ontology<HpoTerm, HpoTermRelation> inheritanceSubontology = null;
    private static Ontology<HpoTerm, HpoTermRelation> abnormalPhenoSubOntology = null;
    /** key -- all lower-case label of a modifer term. Value: corresponding TermId .*/
    private static Map<String, TermId> modifier2TermId = new HashMap<>();

    private Set<SmallFileQCCode> QCissues;

    public OldSmallFileEntry() {
        QCissues = new HashSet<>();
    }
    /** This is called once by client code before we start parsing. Not pretty design but it woirks fine for thuis one-off app. */
    public static void setOntology(HpoOntology ont, Ontology<HpoTerm, HpoTermRelation> inh, Ontology<HpoTerm, HpoTermRelation> phe) {
        ontology = ont;
        inheritanceSubontology = inh;
        abnormalPhenoSubOntology = phe;
        findModifierTerms();
    }

    /**
     * Creates a map for all terms in the Clinical modifier subhierarchy (which
     * starts from HP:0012823). The keys are lower-case versions of the Labels,
     * and the values are the corresponding TermIds. See {@link #modifier2TermId}.
     */
    private static void findModifierTerms() {
        TermId modifier = new ImmutableTermId(HP_PREFIX, "0012823");
        Stack<TermId> stack = new Stack<>();
        Set<TermId> descendents = new HashSet<>();
        stack.push(modifier);
        while (!stack.empty()) {
            TermId parent = stack.pop();
            descendents.add(parent);
            Set<TermId> kids = getChildren(parent);
            kids.forEach(k -> stack.push(k));
        }
        for (TermId tid : descendents) {
            String label = ontology.getTermMap().get(tid).getName().toLowerCase();
            modifier2TermId.put(label, tid);
        }
    }

    private static Set<TermId> getChildren(TermId parent) {
        Set<TermId> kids = new HashSet<>();
        Iterator it = ontology.getGraph().inEdgeIterator(parent);
        while (it.hasNext()) {
            Edge<TermId> sourceEdge = (Edge<TermId>) it.next();
            TermId source = sourceEdge.getSource();
            kids.add(source);
        }
        return kids;
    }


    public void addDiseaseId(String id) {
        if (id.startsWith("OMIM")) {
            this.database = OMIM;
            this.diseaseID = id;
        } else if (id.startsWith("ORPHA")) {
            this.database = ORPHANET;
            this.diseaseID = id;
        } else if (id.startsWith("DECIPHER")) {
            database = DECIPHER;
            this.diseaseID = id;
        } else {
            LOGGER.fatal("Did not recognize disease database for " + id);
            System.exit(1);
        }
    }

    public void addDiseaseName(String n) {
        this.diseaseName = n;
        if (diseaseName.length() < 1) {
            LOGGER.trace("Error zero length name ");
            System.exit(1);
        }
    }


    public void addGeneId(String id) {
        if (id == null) return;
        LOGGER.trace("Adding gene id: " + id);
        this.QCissues.add(GOT_GENE_DATA);
        geneID = id;
    }
    /** Record that we are adding gene data because we will be discarding it. */
    public void setGeneName(String name) {
        if (name==null || name.isEmpty()) return;
        this.QCissues.add(GOT_GENE_DATA);
        geneName = name;
    }

    public void setGenotype(String gt) {
        if (gt==null || gt.isEmpty()) return;
        this.QCissues.add(GOT_GENE_DATA);
        genotype = gt;
    }

    public void setGenesymbol(String gs) {
        if (gs==null || gs.isEmpty()) return;
        this.QCissues.add(GOT_GENE_DATA);
        genesymbol = gs;
    }
    /** Cherck the validating of the String id and crfeate the corresponding TermIKd in {@link #phenotypeId}. */
    public void setPhenotypeId(String id) throws HPOException {
        this.phenotypeId = createHpoTermIdFromString(id);
        TermId primaryId = ontology.getTermMap().get(phenotypeId).getId();
        if (! phenotypeId.equals(primaryId)) {
            phenotypeId=primaryId; // replace alt_id with current primary id.
            this.QCissues.add(UPDATING_ALT_ID);
        }
    }
    /** Sets the name of the HPO term. */
    public void setPhenotypeName(String name) {
        phenotypeName = name;
    }
    /** Sets the age of onset id (HPO term) and checks it is a valid term. */
    public void setAgeOfOnsetId(String id) throws HPOException {
        if (id == null || id.length() == 0) {
            return;
        }// no age of onset
        if (!id.startsWith("HP:")) {
            LOGGER.fatal("Bad phenotype id prefix: " + id);
            System.exit(1);
        }
        if (!(id.length() == 10)) {
            LOGGER.fatal("Bad length for id:  " + id);
            System.exit(1);
        }
        if (!isValidInheritanceTerm(id)) {
            LOGGER.fatal("Not a valid inheritance term....terminating program");
            System.exit(1);
        }
        ageOfOnsetId = createHpoTermIdFromString(id);
    }

    public void setAgeOfOnsetName(String name) {
        if (name == null || name.length() == 0) return; // no age of onset (not required)
        this.ageOfOnsetName = name;
    }

    private boolean isValidInheritanceTerm(String id) throws HPOException {
        TermId tid = createHpoTermIdFromString(id);
        return true;
    }

    /** @return TermId e if this is a well-formed HPO term (starts with HP: and has ten characters).
     * and is listed in the onrtology */
    private TermId createHpoTermIdFromString(String id) throws HPOException  {
        if (!id.startsWith("HP:")) {
            throw new HPOException("Invalid HPO prefix for term id \"" + id + "\"");
        }
        id = id.substring(3);
        TermId tid = new ImmutableTermId(HP_PREFIX, id);
        if (tid == null) {
            throw new HPOException("Could not create TermId object from id: \""+ id+"\"");
        }
        if (ontology == null) {
            throw new HPOException("Ontology is null and thus we could not create TermId for " + id);
        }
        if (!ontology.getTermMap().containsKey(tid)) {
            throw new HPOException("Term " + tid.getIdWithPrefix() + " was not found in the HPO ontology");
        }
        return tid;
    }

    private boolean evidenceCodeWellFormed(String evi) {
        if (evi==null || evi.isEmpty()) return false;
        if ((!evi.equals("IEA")) && (!evi.equals("PCS")) &&
                (!evi.equals("TAS") && (!evi.equals("ICE")))) {
            return false;
        } else {
            return true;
        }
    }

    public void setEvidenceId(String id) throws HPOException {
        this.evidenceID = id;

    }

    public void setEvidenceName(String name) throws HPOException {
        this.evidenceName = name;
        evidenceCodeWellFormed(evidenceName);
    }

    public boolean hasQCissues() {
        if (QCissues.size()==0) return false;
        else if (QCissues.size()==1 && QCissues.contains(UPDATED_DATE_FORMAT)) return false;
        else return true;
    }

    /**
     * This method is called after all of the data have been entered. We return a List of error codes so that
     * we can list up what we had to do to convert the filesd and do targeted manual checking.
     */
    public Set<SmallFileQCCode> doQCcheck() {

        // check the vidence codes. At least one of the three fields
        // has to have one of the correct codes, in order for the V2small file  entry to be ok
        boolean evidenceOK=false;
        if (evidenceID!=null) {
            if (evidenceCodeWellFormed(evidenceID) ) { evidenceOK=true; }
        }
        if (!evidenceOK && evidenceName!=null) {
            if (evidenceCodeWellFormed(evidenceName)) {evidenceOK=true; }
        }
        if (!evidenceOK && evidence!=null) {
            if (evidenceCodeWellFormed(evidence)) {evidenceOK=true; }
        }
        if (!evidenceOK) { QCissues.add(DID_NOT_FIND_EVIDENCE_CODE);}
        // check whether the primary label needs to be updated.
        if (! this.phenotypeName.equals(ontology.getTermMap().get(this.phenotypeId).getName())) {
            this.QCissues.add(UPDATING_HPO_LABEL);
            this.phenotypeName=ontology.getTermMap().get(this.phenotypeId).getName();
        }

        return QCissues;

    }





    public void setFrequencyString(String freq) {
        if (freq == null || freq.length() == 0) return; // not required!
        this.frequencyString = freq.trim();
        if (frequencyString.length() == 0) return; //it ewas just a whitespace
        if (frequencyString.startsWith("HP:")) {
            LOGGER.fatal("NEVER HAPPENS, FREQUENCY WITH TERM");
            System.exit(1);
        } else if (Character.isDigit(frequencyString.charAt(0))) {
            // ok no op
        } else if (frequencyString.equalsIgnoreCase("very rare")) {
            this.frequencyId = VERY_RARE;
        } else if (frequencyString.equalsIgnoreCase("rare")) {
            this.frequencyId = VERY_RARE; //TODO IS THIS OK?
        } else if (frequencyString.equalsIgnoreCase("frequent")) {
            this.frequencyId = FREQUENT;
        } else if (frequencyString.equalsIgnoreCase("occasional")) {
            this.frequencyId = OCCASIONAL;
        } else if (frequencyString.equalsIgnoreCase("variable")) {
            this.frequencyId = FrequencyRoot; //TODO OK -- ?????
        } else if (frequencyString.equalsIgnoreCase("typical")) {
            this.frequencyId = FREQUENT; // TODO OK????????????
        } else if (frequencyString.equalsIgnoreCase("very frequent")) {
            this.frequencyId = VERY_FREQUENT;
        } else if (frequencyString.equalsIgnoreCase("common")) {
            this.frequencyId = FREQUENT; //OK ?????????????????????
        } else if (frequencyString.equalsIgnoreCase("hallmark")) {
            this.frequencyId = VERY_FREQUENT; // OK ?????????????
        } else if (frequencyString.equalsIgnoreCase("obligate")) {
            this.frequencyId = OBLIGATE;
        } else {
            LOGGER.fatal("BAD FREQ ID \"" + freq + "\"");
            System.exit(1);
            //throw new HPOException("Malformed frequencyString: \"" + freq + "\"");
        }
    }


    public void setSexID(String id) throws HPOException {
        if (id == null || id.length() == 0) return;//oik, not required
        if (id.equalsIgnoreCase("MALE"))
            sexID = MALE_CODE;
        else if (id.equalsIgnoreCase("FEMALE"))
            sexID = FEMALE_CODE;
        else
            throw new HPOException("Did not recognize Sex ID: " + id);
    }

    public void setSexName(String name) throws HPOException {
        if (name == null || name.length() == 0) return;//oik, not required
        if (name.equalsIgnoreCase("MALE"))
            sexID = MALE_CODE;
        else if (name.equalsIgnoreCase("FEMALE"))
            sexID = FEMALE_CODE;
        else
            throw new HPOException("Did not recognize Sex Name: " + name);
    }

    public void setNegationID(String id) throws HPOException {
        if (id == null || id.length() == 0) return;
        if (id.equalsIgnoreCase("NOT")) {
            negationID = "NOT";
        } else throw new HPOException("Malformed negation ID: \"" + id + "\"");
    }

    public void setNegationName(String name) throws HPOException {
        if (name == null || name.length() == 0) return;
        if (name.equalsIgnoreCase("NOT")) {
            negationID = "NOT";
        } else throw new HPOException("Malformed negation Name: \"" + name + "\"");
    }


    //(IN 1/4 PATIENTS)
    //1: OMIM-CS:RADIOLOGY > GENERALIZED OSTEOSCLEROSIS
    /**
     * In some case, the Description field will contain a modifier such as {@code Mild}. In  other cases,
     * Seb's pipeline will have added something like {@code MODIFIER:episodic}.
     * Put everything we cannot match like this back into the free text description field ({@link #description}).
     * @param d The description from the original "old" small file.
     */
    public void setDescription(String d) {
        List<String> descriptionList = new ArrayList<>();
         // multiple items. Probably from Sebastian's text mining pipeline
            String A[] = d.split(";");
            for (String a : A) {
                if (a.contains("OMIM-CS")) {
                    this.evidenceID="TAS";
                }
                if (a.startsWith("MODIFIER:")) {
                    String candidateModifier = a.substring(9).toLowerCase();
                    if (candidateModifier.contains("recurrent")) {
                        LOGGER.warn("NEED TO IMPLEMENT RECURRENT");
                        continue;
                    }
                    if (modifier2TermId.containsKey(candidateModifier)) {
                        modifierset.add(modifier2TermId.get(candidateModifier));
                    } else {
                        LOGGER.fatal("Could not identify modifer for " + candidateModifier + ", in description "+d+", terminating program....");
                        System.exit(1); // if this happens we need to add the item to HPO or otherwise check the code!
                    }
                } else if (a.contains("(RARE)")) {
                    if (this.frequencyId==null && this.frequencyString==null) {
                        this.frequencyId=VERY_RARE;
                        this.frequencyString="Very rare";
                    }
                    descriptionList.add(a);
                } else if (a.contains("(IN SOME PATIENTS)")) {
                    if (this.frequencyId==null && this.frequencyString==null) {
                        this.frequencyId=OCCASIONAL;
                        this.frequencyString="Occasional";
                    }
                    descriptionList.add(a);
                } else if (modifier2TermId.containsKey(a.toLowerCase())) {  // exact match (except for capitalization).
                    TermId tid = modifier2TermId.get(a.toLowerCase());
                    this.QCissues.add(CREATED_MODIFER);
                    modifierset.add(tid);
                } else {
                    descriptionList.add(a);
                }
            }

        description = descriptionList.stream().collect(Collectors.joining(";"));
    }


    public void setPub(String p) {
        pub = p;
    }

    public void setAssignedBy(String ab) {
        this.assignedBy = ab;
    }

    public void setDateCreated(String dc) {
        // TODO make all dates look like 2018-01-23
        this.dateCreated = DateUtil.convertToCanonicalDateFormat(dc);
        if (!dc.equals(this.dateCreated)) {
            QCissues.add(UPDATED_DATE_FORMAT);
        }
    }

    public void setAddlEntityName(String n) {
        addlEntityName = n;
    }

    public void setAddlEntityId(String id) {
        addlEntityId = id;
    }

    public void setEntityId(String id) {
        entityId = id;
    }

    public void setEntityName(String name) {
        if (name ==null || name.isEmpty()) return;
        QCissues.add(GOT_EQ_ITEM);
        entityName = name;
    }

    public void setQualityId(String id) {
        if (id ==null || id.isEmpty()) return;
        QCissues.add(GOT_EQ_ITEM);
        qualityId = id;
    }

    public void setQualityName(String name) {
        if (name ==null || name.isEmpty()) return;
        QCissues.add(GOT_EQ_ITEM);
        qualityName = name;
    }

    public void setEvidence(String e) {
        evidence = e;
    }

    public void setAbnormalId(String id) {
        if (id ==null || id.isEmpty()) return;
        QCissues.add(GOT_EQ_ITEM);
        abnormalId = id;
    }

    public void setAbnormalName(String name) {
        if (name ==null || name.isEmpty()) return;
        QCissues.add(GOT_EQ_ITEM);
        abnormalName = name;
    }

    public void setSex(String s) throws HPOException {
        if (s == null) return;
        if (s.equalsIgnoreCase("MALE")) this.sex = MALE_CODE;
        else if (s.equalsIgnoreCase("FEMALE")) this.sex = FEMALE_CODE;
        else throw new HPOException("Did not recognize sex code " + s);
    }

    public DiseaseDatabase getDatabase() {
        return database;
    }

    public String getDiseaseID() {
        return diseaseID;
    }

    public String getDiseaseName() {
        return diseaseName;
    }



    public TermId getPhenotypeId() {
        return phenotypeId;
    }

    public String getPhenotypeName() {
        return phenotypeName;
    }

    public TermId getAgeOfOnsetId() {
        return ageOfOnsetId;
    }

    public String getAgeOfOnsetName() {
        return ageOfOnsetName;
    }

    public String getEvidenceID() {
        return evidenceID;
    }

    public String getEvidenceName() {
        return evidenceName;
    }

    public String getEvidence() {
        return evidence;
    }

    public String getFrequencyString() {
        return frequencyString;
    }

    public TermId getFrequencyId() {
        return frequencyId;
    }

    public String getSex() {
        if (sexID != null) return sexID;
        else if (sexName != null) return sexName;
        else if (sex != null) return sex;
        else return "";
    }

    public String getNegation() {
        if (negationID != null) return negationID;
        else if (negationName != null) return negationName;
        else return "";
    }

    public Set<TermId> getModifierSet() {
        return modifierset;
    }

    public String getModifierString() {
        if (modifierset == null || modifierset.isEmpty()) return "";
        else return modifierset.stream().map(TermId::getIdWithPrefix).collect(Collectors.joining(";"));
    }

    public String getDescription() {
        return description;
    }

    public String getPub() {
        return pub;
    }

    public String getAssignedBy() {
        return assignedBy;
    }

    /**
     * Returns the date created, and transforms the date format to YYYY-MM-DD, e.g., 2009-03-23.
     */
    public String getDateCreated() {
        return convertToCanonicalDateFormat(dateCreated);
    }


}