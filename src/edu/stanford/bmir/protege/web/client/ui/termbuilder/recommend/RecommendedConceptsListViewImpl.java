package edu.stanford.bmir.protege.web.client.ui.termbuilder.recommend;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.common.base.Optional;
import com.google.gwt.user.client.ui.ListBox;
import edu.stanford.bmir.protege.web.client.dispatch.DispatchService;
import edu.stanford.bmir.protege.web.client.dispatch.actions.CreateClassesWithHierarchyAction;
import edu.stanford.bmir.protege.web.client.dispatch.actions.CreateClassesWithHierarchyResult;
import edu.stanford.bmir.protege.web.client.dispatch.actions.RecommendForSingleConceptAction;
import edu.stanford.bmir.protege.web.client.dispatch.actions.RecommendForSingleConceptResult;
import edu.stanford.bmir.protege.web.shared.entity.OWLEntityData;
import edu.stanford.bmir.protege.web.shared.event.EventBusManager;
import edu.stanford.bmir.protege.web.shared.termbuilder.ExtractedConceptsChangedEvent;
import edu.stanford.bmir.protege.web.shared.termbuilder.SourceConceptChangedEvent;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClass;

import com.google.gwt.cell.client.CheckboxCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JsArrayString;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HasText;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.DataGrid;
import com.google.gwt.user.cellview.client.TextColumn;
import com.google.gwt.view.client.AbstractDataProvider;
import com.google.gwt.view.client.DefaultSelectionEventManager;
import com.google.gwt.view.client.MultiSelectionModel;
import com.google.gwt.view.client.ProvidesKey;
import com.google.gwt.view.client.SelectionModel;
import com.google.gwt.widget.client.TextButton;

import edu.stanford.bmir.protege.web.client.dispatch.DispatchServiceManager;
import edu.stanford.bmir.protege.web.client.dispatch.actions.CreateClassesAction;
import edu.stanford.bmir.protege.web.client.dispatch.actions.CreateClassesResult;
import edu.stanford.bmir.protege.web.client.dispatch.actions.RecommendConceptsAction;
import edu.stanford.bmir.protege.web.client.dispatch.actions.RecommendConceptsResult;
import edu.stanford.bmir.protege.web.client.project.Project;
import edu.stanford.bmir.protege.web.client.ui.termbuilder.CompetencyQuestionsManager;
import edu.stanford.bmir.protege.web.client.ui.termbuilder.question.CompetencyQuestionsViewPresenter;
import edu.stanford.bmir.protege.web.shared.DataFactory;
import edu.stanford.bmir.protege.web.shared.termbuilder.Concept;
import edu.stanford.bmir.protege.web.shared.termbuilder.RecommendedConceptInfo;
import org.semanticweb.owlapi.model.OWLEntity;

public class RecommendedConceptsListViewImpl extends Composite implements RecommendedConceptsListView {

	private final Project project;
	private  RecommendedConceptsListViewPresenter presenter = null;
    private RecommendedConceptsPortlet portlet = null;
	
	private static RecommendedConceptsListViewImplUiBinder uiBinder = GWT
			.create(RecommendedConceptsListViewImplUiBinder.class);

	interface RecommendedConceptsListViewImplUiBinder extends
			UiBinder<Widget, RecommendedConceptsListViewImpl> {
	}
	
	private final String CANDIDATE_CONCEPT_COL_TITLE = "Candidate Concepts";
	private final String RELATION_COL_TITLE = "Relation to Existing Concepts";
	private final String SELECT_COL_TITLE = "Select";
	private final String EMPTY_TABLE_LABEL = "There is no recommendations to show.";
	
	final MultiSelectionModel<RecommendedConceptInfo> selectionModel;
	
	@UiField(provided=true) DataGrid<RecommendedConceptInfo> dataGrid;
    //@UiField ListBox listBox;
    //@UiField Button submitButton;
	@UiField Button refreshButton;
	@UiField Button acceptButton;

	public RecommendedConceptsListViewImpl(Project project, RecommendedConceptsPortlet portlet) {
		ProvidesKey<RecommendedConceptInfo> providesKey = new ProvidesKey<RecommendedConceptInfo>() {
			@Override
			public Object getKey(RecommendedConceptInfo item) {
				return item.getId();
			}
		};
		
		this.project = project;
        this.portlet = portlet;
		
		dataGrid = new DataGrid<RecommendedConceptInfo>(1000, providesKey);
		initWidget(uiBinder.createAndBindUi(this));
		dataGrid.setAutoHeaderRefreshDisabled(true);
		dataGrid.setEmptyTableWidget(new Label(this.EMPTY_TABLE_LABEL));
		selectionModel =
				new MultiSelectionModel<RecommendedConceptInfo>(providesKey);
		dataGrid.setSelectionModel(selectionModel, DefaultSelectionEventManager
				.<RecommendedConceptInfo> createCheckboxManager());
		initTableColumns(selectionModel);
		
	}
	
	private void initTableColumns(
			final SelectionModel<RecommendedConceptInfo> selectionModel) {
		
		TextColumn<RecommendedConceptInfo> candidateConceptColumn = 
				new TextColumn<RecommendedConceptInfo> () {
					@Override
					public String getValue(RecommendedConceptInfo object) {						
						return object.getRecommendedConcept().getConceptName();
					}
		};
		dataGrid.addColumn(candidateConceptColumn, this.CANDIDATE_CONCEPT_COL_TITLE);
		dataGrid.setColumnWidth(candidateConceptColumn, 25, Unit.PCT);
		
		TextColumn<RecommendedConceptInfo> relationColumn = 
				new TextColumn<RecommendedConceptInfo> () {
					@Override
					public String getValue(RecommendedConceptInfo object) {						
						return object.getConceptRelationDescription();
					}
		};
		dataGrid.addColumn(relationColumn, this.RELATION_COL_TITLE);
		dataGrid.setColumnWidth(relationColumn, 65, Unit.PCT);
		
		Column<RecommendedConceptInfo, Boolean> checkColumn =
				new Column<RecommendedConceptInfo, Boolean>(new CheckboxCell(true, false)) {
					@Override
					public Boolean getValue(RecommendedConceptInfo object) {
						return selectionModel.isSelected(object);
					}
		};
		dataGrid.addColumn(checkColumn, this.SELECT_COL_TITLE);
		dataGrid.setColumnWidth(checkColumn, 10, Unit.PCT);
	}

	@Override
    public Widget getWidget() {
        return this;
    }

	@UiHandler("refreshButton")
	void onRefreshButtonClick(ClickEvent event) {
		onRefresh();
	}
	
	private void onRefresh() {
        /* // Old implementation that support recommending for all accepted concepts.
        Set<Concept> conceptSet = project.getCompetencyQuestionsManager().getAcceptedConcepts();
		System.err.println("[Client] Accepted Concepts Number: " + conceptSet.size());
		HashSet<Concept> conceptHashSet = new HashSet<Concept>(conceptSet);
		RecommendConceptsAction action = new RecommendConceptsAction(project.getProjectId(), conceptHashSet);
		DispatchServiceManager.get().execute(action, getRecommendConceptsActionAsyncHandler());
		*/

        // New implementation that only recommend for selected conceptName
        Optional<OWLEntityData> entity = portlet.getSelectedEntityData();
        if(!entity.isPresent()) return;
        String className = entity.get().getBrowserText();
        IRI classIRI = entity.get().getEntity().getIRI();
        System.out.println("IRI: " + classIRI.toString());
        RecommendForSingleConceptAction action = new RecommendForSingleConceptAction(project.getProjectId(), new Concept(className, classIRI));
        DispatchServiceManager.get().execute(action, getRecommendForSingleConceptActionAsyncHandler());
	}

    private AsyncCallback<RecommendForSingleConceptResult> getRecommendForSingleConceptActionAsyncHandler() {
        return new AsyncCallback<RecommendForSingleConceptResult>() {

            @Override
            public void onFailure(Throwable caught) {
                try {
                    System.err.println("[Client] Recommend Concept Action Handling Error!");
                    throw caught;
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onSuccess(RecommendForSingleConceptResult result) {
                System.err.println("[Client] Recommend Concept Action Handling Succeed!");
                CompetencyQuestionsManager manager = project.getCompetencyQuestionsManager();
                manager.addRecommendedConcepts(result.getRecommendedConcepts());
                EventBusManager.getManager().postEvent(new SourceConceptChangedEvent(project.getProjectId()));
                presenter.reload();
            }
        };
    }
	
	private AsyncCallback<RecommendConceptsResult> getRecommendConceptsActionAsyncHandler() {
		return new AsyncCallback<RecommendConceptsResult>() {
			
			@Override
			public void onFailure(Throwable caught) {
				try {
					System.err.println("[Client] Recommend Concept Action Handling Error!");
					throw caught;
				} catch (Throwable e) {
					e.printStackTrace();
				}
			}
			
			@Override
			public void onSuccess(RecommendConceptsResult result) {
				System.err.println("[Client] Recommend Concept Action Handling Succeed!");
				CompetencyQuestionsManager manager = project.getCompetencyQuestionsManager();
				manager.addRecommendedConcepts(result.getRecommendedConcepts());
				presenter.reload();
			}
			
		};
	}

	@UiHandler("acceptButton")
	void onAcceptButtonClick(ClickEvent event) {
		onAccept();
	}
	
	private void onAccept() {
		//Get CQ Manager
		CompetencyQuestionsManager manager = project.getCompetencyQuestionsManager();
		
		Set<RecommendedConceptInfo> selectedSet = selectionModel.getSelectedSet();
		List<String> selectedClassesArray = new ArrayList<String>();
        List<ClassStringAndSuperClassPair> pairList = new ArrayList<ClassStringAndSuperClassPair>();
		
        populateAcceptedClassesInfo(selectedSet, selectedClassesArray, pairList);
		
		manager.addAcceptedConceptsFromString(selectedClassesArray);
		Window.alert("You have accepted " + selectedClassesArray.size() + " concepts!");
		
		/*
		//Add classes into class tree, naive implementation simply add all concepts as the subclass of Thing
		final OWLClass superCls = DataFactory.getOWLThing();
		final Set<String> newClasses = new HashSet<String>(selectedClassesArray);
		DispatchServiceManager.get().execute(new CreateClassesAction(project.getProjectId(), superCls, newClasses),
				getCreateClassesActionAsyncHandler());
	    */

        //Add classes into class tree, with class hierarchy information
        DispatchServiceManager.get().execute(new CreateClassesWithHierarchyAction(project.getProjectId(), pairList),
                getCreateClassesWithHierarchyActionAsyncHandler());

	}

    // TODO: support more class hierarchy analysis when adding classes into class tree
    private void populateAcceptedClassesInfo(Set<RecommendedConceptInfo> infoSet, List<String> selectedClassesArray, List<ClassStringAndSuperClassPair> pairList) {
        for(RecommendedConceptInfo info: infoSet) {
            String conceptName = info.getRecommendedConcept().getConceptName();
            selectedClassesArray.add(conceptName);
            OWLClass superClass;
            if(info.getRelation() == RecommendedConceptInfo.ConceptRelation.SUBCLASS_OF || info.getRelation() == RecommendedConceptInfo.ConceptRelation.PART_OF) {
                superClass = DataFactory.getOWLClass(info.getSrcConcept().getIRI());
            } else {
                superClass = DataFactory.getOWLThing();
            }
            pairList.add(new ClassStringAndSuperClassPair(conceptName, superClass));
        }
    }
	
	private AsyncCallback<CreateClassesResult> getCreateClassesActionAsyncHandler() {
        return new AsyncCallback<CreateClassesResult>() {
            @Override
            public void onFailure(Throwable caught) {
                GWT.log("There was a problem creating the classes.  Please try again.");
            }

            @Override
            public void onSuccess(CreateClassesResult result) {
            	System.err.println("Classes added into the tree!");
            }
        };
    }

    private AsyncCallback<CreateClassesWithHierarchyResult> getCreateClassesWithHierarchyActionAsyncHandler() {
        return new AsyncCallback<CreateClassesWithHierarchyResult>() {
            @Override
            public void onFailure(Throwable caught) {
                GWT.log("There was a problem creating the classes.  Please try again.");
            }

            @Override
            public void onSuccess(CreateClassesWithHierarchyResult result) {
                System.err.println("Classes added into the tree!");
            }
        };
    }

	@Override
	public void setDataProvider(
			AbstractDataProvider<RecommendedConceptInfo> dataProvider) {
		dataProvider.addDataDisplay(dataGrid);
	}
	
	@Override
	public void setViewPresenter(RecommendedConceptsListViewPresenter presenter) {
		this.presenter = presenter;
	}
	
	@Override
	public CompetencyQuestionsManager getCompetencyQuestionsManager() {
		return project.getCompetencyQuestionsManager();
	}
}