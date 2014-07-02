package edu.stanford.bmir.protege.web.server.dispatch.handlers;

import com.google.common.base.Optional;
import edu.stanford.bmir.protege.web.client.dispatch.actions.CreateClassesWithHierarchyAction;
import edu.stanford.bmir.protege.web.client.dispatch.actions.CreateClassesWithHierarchyResult;
import edu.stanford.bmir.protege.web.shared.termbuilder.ClassStringAndSuperClassPair;
import edu.stanford.bmir.protege.web.server.change.ChangeApplicationResult;
import edu.stanford.bmir.protege.web.server.change.ChangeDescriptionGenerator;
import edu.stanford.bmir.protege.web.server.change.CreateClassesChangeGenerator;
import edu.stanford.bmir.protege.web.server.change.FixedMessageChangeDescriptionGenerator;
import edu.stanford.bmir.protege.web.server.dispatch.AbstractHasProjectActionHandler;
import edu.stanford.bmir.protege.web.server.dispatch.ExecutionContext;
import edu.stanford.bmir.protege.web.server.dispatch.RequestContext;
import edu.stanford.bmir.protege.web.server.dispatch.RequestValidator;
import edu.stanford.bmir.protege.web.server.dispatch.validators.UserHasProjectReadPermissionValidator;
import edu.stanford.bmir.protege.web.server.msg.OWLMessageFormatter;
import edu.stanford.bmir.protege.web.server.owlapi.OWLAPIProject;
import edu.stanford.bmir.protege.web.shared.BrowserTextMap;
import edu.stanford.bmir.protege.web.shared.ObjectPath;
import edu.stanford.bmir.protege.web.shared.event.ProjectEvent;
import edu.stanford.bmir.protege.web.shared.events.EventList;
import edu.stanford.bmir.protege.web.shared.events.EventTag;
import org.semanticweb.owlapi.model.OWLClass;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The handler for CreateClassesWithHierarchyAction.
 *
 * @author Yuhao Zhang <zyh@stanford.edu>
 */
public class CreateClassesWithHierarchyActionHandler extends AbstractHasProjectActionHandler<CreateClassesWithHierarchyAction,CreateClassesWithHierarchyResult> {
    @Override
    public Class<CreateClassesWithHierarchyAction> getActionClass() {
        return CreateClassesWithHierarchyAction.class;
    }

    @Override
    protected RequestValidator<CreateClassesWithHierarchyAction> getAdditionalRequestValidator(CreateClassesWithHierarchyAction action, RequestContext requestContext) {
        return UserHasProjectReadPermissionValidator.get();
    }

    @Override
    protected CreateClassesWithHierarchyResult execute(CreateClassesWithHierarchyAction action, OWLAPIProject project, ExecutionContext executionContext) {
        EventTag tag = project.getEventManager().getCurrentTag();

        List<ClassStringAndSuperClassPair> pairList = action.getPairList();
        Set<OWLClass> superClasses = new HashSet<OWLClass>();
        Set<OWLClass> createdClasses = new HashSet<OWLClass>();
        Set<ObjectPath<OWLClass>> pathToRootSet = new HashSet<ObjectPath<OWLClass>>();

        for(ClassStringAndSuperClassPair pair : pairList) {
            Set<List<OWLClass>> paths = project.getClassHierarchyProvider().getPathsToRoot(pair.getSuperClass());
            if(paths.isEmpty()) {
                throw new IllegalStateException("Class does not exist in hierarchy: " + project.getRenderingManager().getBrowserText(pair.getSuperClass()));
            }
            ObjectPath<OWLClass> pathToRoot = new ObjectPath<OWLClass>(paths.iterator().next());
            Set<String> tempSet = new HashSet<String>();
            tempSet.add(pair.getClassName());
            final CreateClassesChangeGenerator gen = new CreateClassesChangeGenerator(tempSet, Optional.of(pair.getSuperClass()));
            ChangeApplicationResult<Set<OWLClass>> result = project.applyChanges(executionContext.getUserId(), gen, createChangeText(project, pair.getClassName(), pair.getSuperClass()));
            createdClasses.addAll(result.getSubject().get());
            superClasses.add(pair.getSuperClass());
            pathToRootSet.add(pathToRoot);
        }

        BrowserTextMap browserTextMap = BrowserTextMap.build(project.getRenderingManager(), superClasses, createdClasses);

        EventList<ProjectEvent<?>> eventList = project.getEventManager().getEventsFromTag(tag);

        return new CreateClassesWithHierarchyResult(browserTextMap, pathToRootSet, createdClasses, eventList);
    }

    private ChangeDescriptionGenerator<Set<OWLClass>> createChangeText(OWLAPIProject project, String className, OWLClass superClass) {
        return new FixedMessageChangeDescriptionGenerator<Set<OWLClass>>(OWLMessageFormatter.formatMessage("Created {0} as subclasses of {1}", project, className, superClass));
    }
}
