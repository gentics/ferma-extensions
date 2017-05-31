
package com.gentics.ferma;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.gentics.ferma.model.Group;
import com.gentics.ferma.model.Person;
import com.gentics.ferma.orientdb.DelegatingFramedOrientGraph;
import com.gentics.ferma.orientdb.DelegatingFramedTransactionalOrientGraph;
import com.orientechnologies.orient.core.metadata.schema.OClass;
import com.orientechnologies.orient.core.metadata.schema.OType;
import com.tinkerpop.blueprints.impls.orient.OrientEdgeType;
import com.tinkerpop.blueprints.impls.orient.OrientGraph;
import com.tinkerpop.blueprints.impls.orient.OrientGraphNoTx;
import com.tinkerpop.blueprints.impls.orient.OrientVertex;
import com.tinkerpop.blueprints.impls.orient.OrientVertexType;

public class OrientDBFermaIndexTest extends AbstractOrientDBTest {

	private final static int nMembers = 2000;
	private final static int nChecks = 4000;

	@Test
	public void testOrientVerticleClass() {
		try (Tx tx = graph.tx()) {
			Person p = tx.getGraph().addFramedVertex(Person.class);
			p.setName("personName");
			assertEquals(Person.class.getSimpleName(), ((OrientVertex) p.getElement()).getLabel());
			tx.success();
		}
	}

	/**
	 * Setup some indices. This is highly orientdb specific and may not be easy so setup using blueprint API.
	 */
	private void setupTypesAndIndices() {
		try (Tx tx = graph.tx()) {
			OrientGraphNoTx g = ((OrientGraphNoTx) ((DelegatingFramedOrientGraph) tx.getGraph()).getBaseGraph());
			// g.setUseClassForEdgeLabel(true);
			g.setUseLightweightEdges(false);
			g.setUseVertexFieldsForEdgeLabels(false);
		}

		try (Tx tx = graph.tx()) {
			OrientGraphNoTx g = ((OrientGraphNoTx) ((DelegatingFramedOrientGraph) tx.getGraph()).getBaseGraph());

			OrientEdgeType e = g.createEdgeType("HAS_MEMBER");
			e.createProperty("in", OType.LINK);
			e.createProperty("out", OType.LINK);
			e.createIndex("e.has_member", OClass.INDEX_TYPE.UNIQUE_HASH_INDEX, "out", "in");

			OrientVertexType v = g.createVertexType(Group.class.getSimpleName(), "V");
			v.createProperty("name", OType.STRING);

			v = g.createVertexType(Person.class.getSimpleName(), "V");
			v.createProperty("name", OType.STRING);
			v.createIndex(Person.class.getSimpleName() + ".name", OClass.INDEX_TYPE.UNIQUE_HASH_INDEX, "name");
		}

	}

	@Test
	public void testFermaIndexUsage() {

		setupTypesAndIndices();

		// Create a group with x persons assigned to it.
		List<Person> persons = new ArrayList<>();
		Group g;
		try (Tx tx = graph.tx()) {
			g = tx.getGraph().addFramedVertex(Group.class);
			g.setName("groupName");
			for (int i = 0; i < nMembers; i++) {
				Person p = tx.getGraph().addFramedVertex(Person.class);
				p.setName("personName_" + i);
				g.addMember(p);
				persons.add(p);
			}
			tx.success();
		}

		try (Tx tx = graph.tx()) {
			OrientGraph graph = ((OrientGraph) ((DelegatingFramedTransactionalOrientGraph) tx.getGraph()).getBaseGraph());
			assertEquals(nMembers, g.getMembers().size());
			long start = System.currentTimeMillis();
			for (int i = 0; i < nChecks; i++) {
				int nPerson = (int) (Math.random() * persons.size());
				String name = "personName_" + nPerson;

				// assertEquals(name, tx.getGraph().getFramedVerticesExplicit("Person.name", name, Person.class).iterator().next().getName());

				assertTrue(tx.getGraph().getFramedVerticesExplicit("Person.name", name, Person.class).iterator().hasNext());

				// OrientDB specific api
				// Iterable<Vertex> vertices = graph.getVertices(Person.class.getSimpleName(), new String[] { "name" },new Object[] {name});
				// assertTrue(vertices.iterator().hasNext());
			}
			long dur = System.currentTimeMillis() - start;
			double perCheck = ((double) dur / (double) nChecks);
			System.out.println("[graph.getVertices] Duration per lookup: " + perCheck);
			System.out.println("[graph.getVertices] Duration: " + dur);

		}
	}

}
