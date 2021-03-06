/*
 * Copyright (C) 2014-2015, VistaTEC or third-party contributors as indicated
 * by the @author tags or express copyright attribution statements applied by
 * the authors. All third-party contributions are distributed under license by
 * VistaTEC.
 *
 * This file is part of Ocelot.
 *
 * Ocelot is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Ocelot is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, write to:
 *
 *     Free Software Foundation, Inc.
 *     51 Franklin Street, Fifth Floor
 *     Boston, MA 02110-1301
 *     USA
 *
 * Also, see the full LGPL text here: <http://www.gnu.org/copyleft/lesser.html>
 */
package com.vistatec.ocelot.xliff.okapi;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import net.sf.okapi.lib.xliff2.Const;
import net.sf.okapi.lib.xliff2.changeTracking.ChangeTrack;
import net.sf.okapi.lib.xliff2.changeTracking.Item;
import net.sf.okapi.lib.xliff2.changeTracking.Revision;
import net.sf.okapi.lib.xliff2.changeTracking.Revisions;
import net.sf.okapi.lib.xliff2.core.Fragment;
import net.sf.okapi.lib.xliff2.core.MTag;
import net.sf.okapi.lib.xliff2.core.Part;
import net.sf.okapi.lib.xliff2.core.Segment;
import net.sf.okapi.lib.xliff2.core.Tag;
import net.sf.okapi.lib.xliff2.core.TagType;
import net.sf.okapi.lib.xliff2.core.Unit;
import net.sf.okapi.lib.xliff2.its.IITSItem;
import net.sf.okapi.lib.xliff2.its.ITSItems;
import net.sf.okapi.lib.xliff2.its.ITSWriter;
import net.sf.okapi.lib.xliff2.its.LocQualityIssue;
import net.sf.okapi.lib.xliff2.its.LocQualityIssues;
import net.sf.okapi.lib.xliff2.its.Provenances;
import net.sf.okapi.lib.xliff2.reader.Event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vistatec.ocelot.config.UserProvenance;
import com.vistatec.ocelot.events.ProvenanceAddEvent;
import com.vistatec.ocelot.events.api.OcelotEventQueue;
import com.vistatec.ocelot.its.model.LanguageQualityIssue;
import com.vistatec.ocelot.its.model.Provenance;
import com.vistatec.ocelot.its.model.okapi.OkapiProvenance;
import com.vistatec.ocelot.segment.model.OcelotSegment;
import com.vistatec.ocelot.segment.model.okapi.FragmentVariant;
import com.vistatec.ocelot.segment.model.okapi.Note;
import com.vistatec.ocelot.segment.model.okapi.OkapiSegment;
import com.vistatec.ocelot.xliff.XLIFFWriter;

/**
 * Write out XLIFF 2.0 files.
 */
public class OkapiXLIFF20Writer implements XLIFFWriter {
	
    private static final Logger LOG = LoggerFactory.getLogger(OkapiXLIFF20Writer.class);
    private final OkapiXLIFF20Parser parser;
    private final UserProvenance userProvenance;
    private final OcelotEventQueue eventQueue;

    public OkapiXLIFF20Writer(OkapiXLIFF20Parser parser, UserProvenance userProvenance,
            OcelotEventQueue eventQueue) {
        this.parser = parser;
        this.userProvenance = userProvenance;
        this.eventQueue = eventQueue;
    }

    @Override
    public void updateSegment(OcelotSegment seg) {
        OkapiSegment okapiSeg = (OkapiSegment) seg;
        Segment unitPart = this.parser.getSegmentUnitPart(okapiSeg.eventNum);
        if (unitPart == null) {
            LOG.error("Failed to find Okapi Unit Part associated with segment #"+okapiSeg.getSegmentNumber());

        } else if (unitPart.isSegment()) {
            if (okapiSeg.hasOriginalTarget()) {
                FragmentVariant targetFrag = (FragmentVariant) okapiSeg.getTarget();
                Fragment updatedOkapiFragment = targetFrag.getUpdatedOkapiFragment(unitPart.getTarget());
                unitPart.setTarget(updatedOkapiFragment);
                manageRevision(this.parser.getSegmentEvent(okapiSeg.getSegmentNumber()), unitPart, parser.getTargetVersion(okapiSeg.eventNum));
            }
            
            updateITSLQIAnnotations(unitPart, okapiSeg);

            if (!haveAddedOcelotProvAnnotation(unitPart, okapiSeg)) {
                updateITSProvAnnotations(unitPart, okapiSeg);
            }

            FragmentVariant source = (FragmentVariant) okapiSeg.getSource();
            source.updateSegmentAtoms(unitPart);

            FragmentVariant target = (FragmentVariant) okapiSeg.getTarget();
            target.updateSegmentAtoms(unitPart);
            target.setAtomsHighlightedText();

        } else {
            LOG.error("Unit part associated with Segment was not an Okapi Segment!");
            LOG.error("Failed to update Unit Part for segment #"+okapiSeg.getSegmentNumber());
        }
    }
    
    

    private void updateNotes(Event segmentEvent, OkapiSegment okapiSeg) {
	    
    	if(segmentEvent.isUnit()){
    		Unit unit = segmentEvent.getUnit();
    		Note ocelotNote = null;
    		if(okapiSeg.getNotes() != null){
    			ocelotNote = okapiSeg.getNotes().getOcelotNote();
    		}
    		if(ocelotNote != null){
    			net.sf.okapi.lib.xliff2.core.Note okapiNote = findOcelotNoteInUnit(unit);
    			//CASE 1 - note created for this segment
    			if(okapiNote == null){
    				okapiNote = new net.sf.okapi.lib.xliff2.core.Note(ocelotNote.getContent());
    				okapiNote.setId(ocelotNote.getId());
    				unit.addNote(okapiNote);
    				//CASE 2 - note changed for this segment	
    			} else {
    				okapiNote.setText(ocelotNote.getContent());
    			}
    		} else {
    			net.sf.okapi.lib.xliff2.core.Note okapiNote = findOcelotNoteInUnit(unit);
    			//CASE 3 - note deleted for this segment
    			if(okapiNote != null){
    				unit.getNotes().remove(okapiNote);
    			}
    		}
    	}
    }
    
    private net.sf.okapi.lib.xliff2.core.Note findOcelotNoteInUnit(Unit unit ){
    	
    	net.sf.okapi.lib.xliff2.core.Note note = null;
    	if(unit.getNotes() != null){
    		for(net.sf.okapi.lib.xliff2.core.Note currNote: unit.getNotes()){
    			if(currNote.getId().startsWith(Note.OCELOT_ID_PREFIX)){
    				note = currNote;
    				break;
    			}
    		}
    	}
    	return note;
    }
    
	private void manageRevision(Event event, Segment unitPart, TargetVersion nextVersion) {

    	if(event.isUnit()){
    		Unit unit =  event.getUnit();
    		Item item = null;
    		Date now = new Date();
    		if(!unit.hasChangeTrack()){
    			ChangeTrack changeTrack = new ChangeTrack();
    			unit.setChangeTrack(changeTrack);
    			Revisions revisions = createTargetRevisions(nextVersion.getVersion());
    			changeTrack.add(revisions);
    			Revision revision = createCurrentRevision(nextVersion.getVersion(), parser.getRevisionDateFormatter().format(now));
    			revisions.add(revision);
    			item = new Item();
    			item.setProperty(Item.PROPERTY_CONTENT_VALUE);
    			revision.add(item);
    		} else {
    			Revisions targetRevisions = null;
    			Iterator<Revisions> revsIt = unit.getChangeTrack().iterator();
    			Revisions revs = null;
    			while(revsIt.hasNext()) {
    				revs = revsIt.next();
    				if(revs.getAppliesTo().equals(Const.ELEM_TARGET)){
    					targetRevisions = revs;
    					break;
    				}
    			}
    			if(targetRevisions == null){
    				targetRevisions = createTargetRevisions(nextVersion.getVersion());
    				unit.getChangeTrack().add(targetRevisions);
    			}
    			Revision currentRevision = null;
    			for(Revision rev: targetRevisions){
    				if(rev.getVersion().equals(nextVersion.getVersion())){
    					currentRevision = rev;
    					break;
    				}
    			}
    			if(currentRevision == null){
    				currentRevision = createCurrentRevision(nextVersion.getVersion(), parser.getRevisionDateFormatter().format(now));
    				targetRevisions.add(currentRevision);
    				targetRevisions.setCurrentVersion(nextVersion.getVersion());
    			}
    			Iterator<Item> itemsIt = currentRevision.iterator();
    			Item currItem = null;
    			while(itemsIt.hasNext()){
    				currItem = itemsIt.next();
    				if(currItem.getProperty().equals(Item.PROPERTY_CONTENT_VALUE)){
    					item = currItem;
    					break;
    				}
    			}
    			if(item == null){
    				item = new Item();
    				item.setProperty(Item.PROPERTY_CONTENT_VALUE);
    				currentRevision.add(item);
    			}
    		}
    		item.setText(unitPart.getTarget().toString());
    		nextVersion.setUpdated(true);
    	}
    }
    
    private Revisions createTargetRevisions(String version){
    	
    	Revisions targetRevisions = new Revisions();
    	targetRevisions.setAppliesTo(Const.ELEM_TARGET);
    	targetRevisions.setCurrentVersion(version);
    	return targetRevisions;
    }
    
    private Revision createCurrentRevision(String version, String date){
    	
    	Revision revision = new Revision();
		revision.setVersion(version);
		revision.setDatetime(date);
		return revision;
    }
    

	/**
     * Records the Ocelot ITS Provenance record to the Okapi segment
     * representation used when saving the file using the Okapi XLIFF 2.0 writer.
     * @param unitPart - Okapi representation of the segment to annotate
     * @param seg - Ocelot segment
     */
    private void updateITSProvAnnotations(Part unitPart, OcelotSegment seg) {
        Provenances okapiOcelotProv = getOkapiOcelotProvenance(seg);
        if (okapiOcelotProv != null) {
            ITSWriter.annotate(unitPart.getTarget(), 0, -1, okapiOcelotProv);

            Provenance ocelotProv = new OkapiProvenance(okapiOcelotProv.getList().get(0));
            eventQueue.post(new ProvenanceAddEvent(ocelotProv, seg, true));
        }
    }

    /**
     * Constructs the Okapi representation of the Ocelot ITS Provenance record
     * if the reviewer's profile is set.
     * @param seg - Ocelot segment
     * @return - Okapi ITS Provenance Object or null if profile {@link UserProvenance} is not set
     */
    private Provenances getOkapiOcelotProvenance(OcelotSegment seg) {
        if (userProvenance.isEmpty()) {
            return null;
        }

        String ocelotProvId = "OcelotProv" + seg.getSegmentNumber();
        Provenances okapiProvGroup = new Provenances(ocelotProvId);

        net.sf.okapi.lib.xliff2.its.Provenance okapiProv
                = new net.sf.okapi.lib.xliff2.its.Provenance();
        okapiProv.setRevPerson(userProvenance.getRevPerson());
        okapiProv.setRevOrg(userProvenance.getRevOrg());
        okapiProv.setRevTool("http://open.vistatec.com/ocelot");
        okapiProv.setProvRef(userProvenance.getProvRef());
        okapiProvGroup.getList().add(okapiProv);

        return okapiProvGroup;
    }

    private boolean haveAddedOcelotProvAnnotation(Part unitPart, OcelotSegment seg) {
        boolean haveAddedUserProv = seg.hasOcelotProvenance();

        List<Tag> targetTags = unitPart.getTarget().getOwnTags();
        for (Tag tag : targetTags) {
            if (tag.isMarker()) {
                MTag mtag = (MTag) tag;

                if (mtag.hasITSItem()) {
                    Provenances provMetadata = (Provenances) mtag.getITSItems()
                            .get(net.sf.okapi.lib.xliff2.its.Provenance.class);
                    if (provMetadata != null
                            && provMetadata.getGroupId().matches("OcelotProv[0-9]*")) {
                        haveAddedUserProv = true;
                    }
                }
            }
        }
        return haveAddedUserProv;
    }

    /**
     * Update LQI annotations on the segment. TODO: separate from non-LQI updates
     * @param unitPart - Okapi representation of the segment
     * @param seg - Ocelot segment
     */
    public void updateITSLQIAnnotations(Part unitPart, OcelotSegment seg) {
        removeExistingLqiAnnotationsFromSegment(unitPart);

        if (!seg.getLQI().isEmpty()) {
            String ocelotLqiId = "OcelotLQI" + seg.getSegmentNumber();
            LocQualityIssues newOkapiLqiGroup = convertOcelotToOkapiLqi(
                    seg.getLQI(), ocelotLqiId);
            ITSWriter.annotate(unitPart.getTarget(), 0, -1, newOkapiLqiGroup);
        }
    }

    private void removeExistingLqiAnnotationsFromSegment(Part unitPart) {
        List<Tag> sourceTags = unitPart.getSource().getOwnTags();
        List<Tag> targetTags = unitPart.getTarget().getOwnTags();

        removeExistingLqiAnnotations(unitPart, false, sourceTags);
        removeExistingLqiAnnotations(unitPart, true, targetTags);
    }

    private void removeExistingLqiAnnotations(Part unitPart, boolean isTarget, List<Tag> tags) {
        for (Tag tag : tags) {
            if (tag.isMarker()) {
                MTag mtag = (MTag) tag;
                if (mtag.hasITSItem()) {
                    ITSItems items = mtag.getITSItems();
                    IITSItem itsLqiItem = items.get(LocQualityIssue.class);
                    if (itsLqiItem != null) {
                        // Don't delete the LQI issues for opening tags so we
                        // can find the corresponding closing tag and delete it as well
                        if (mtag.getTagType() == TagType.CLOSING ||
                                mtag.getTagType() == TagType.STANDALONE) {
                            items.remove(itsLqiItem);
                        }
                        // TODO: Assumes MTag is only used for ITS metadata
                        if (items.size() <= 1) {
                            Fragment frag = isTarget ?
                                    unitPart.getTarget() : unitPart.getSource();
                            frag.remove(mtag);
                        }
                    }
                }
            }
        }
    }

    private LocQualityIssues convertOcelotToOkapiLqi(List<LanguageQualityIssue> ocelotLqi, String ocelotLqiId) {
        LocQualityIssues newLqiGroup = new LocQualityIssues(ocelotLqiId);
        for (LanguageQualityIssue lqi : ocelotLqi) {
            LocQualityIssue newLqi = new LocQualityIssue();
            newLqi.setType(lqi.getType());
            newLqi.setComment(lqi.getComment());
            newLqi.setSeverity(lqi.getSeverity());

            if (lqi.getProfileReference() != null) {
                newLqi.setProfileRef(lqi.getProfileReference().toString());
            }

            newLqi.setEnabled(lqi.isEnabled());
            newLqiGroup.getList().add(newLqi);
        }
        return newLqiGroup;
    }

    @Override
    public void save(File file) throws IOException, UnsupportedEncodingException {
        net.sf.okapi.lib.xliff2.writer.XLIFFWriter writer = new net.sf.okapi.lib.xliff2.writer.XLIFFWriter();
        StringWriter tmp = new StringWriter();
        writer.create(tmp, parser.getSourceLang());
        writer.setLineBreak("\n"); //FIXME: OS linebreak detection in XLIFF filter doesn't seem to work (Mac) so we need to set it.
        writer.setWithOriginalData(true);
        for (Event event : parser.getEvents()) {
            writer.writeEvent(event);
        }
        writer.close();
        tmp.close();
        Writer outputFile = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(file.getAbsolutePath()), "UTF-8"));
        outputFile.write(tmp.toString());
        outputFile.flush();
        outputFile.close();
        parser.updateTargetVersions();
    }

	@Override
	public void updateNotes(OcelotSegment seg) {

		OkapiSegment okapiSeg = (OkapiSegment) seg;
		updateNotes(this.parser.getSegmentEvent(okapiSeg.getSegmentNumber()),
		        okapiSeg);

	}

}
