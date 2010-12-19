package org.amanzi.index;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;

import org.amanzi.index.config.DefaultIndexConfig;
import org.amanzi.index.config.DefaultPropertyConfig;
import org.amanzi.index.config.IndexConfig;
import org.amanzi.index.config.PropertyConfig;
import org.amanzi.index.mappers.CharacterStringMapper;
import org.amanzi.index.mappers.FloatMapper;
import org.amanzi.index.mappers.IntegerMapper;
import org.amanzi.index.mappers.Mapper;
import static org.amanzi.index.util.IndexUtilities.arrayString;
import org.junit.Test;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;

public class TestIndex extends Neo4jTestCase {
	private abstract class PropertyGenerator<T extends Object> extends DefaultPropertyConfig<T> {
		protected Random random;

		public PropertyGenerator(String name, Random random, T min, T max) {
			super(name, min, max);
			this.random = random;
		}

		public abstract T next();
	}

	private class StringPropertyGenerator extends PropertyGenerator<String> {

		public StringPropertyGenerator(String name, Random random, String min, String max) {
			super(name, random, min, max);
		}

		public String next() {
			StringBuffer sb = new StringBuffer();
			int len = 2 + random.nextInt(8);
			for (int i = 0; i < len; i++) {
				char cmin = min.charAt(Math.min(i, min.length() - 1));
				char cmax = max.charAt(Math.min(i, max.length() - 1));
				sb.append((char) (cmin + random.nextInt(cmax - cmin)));
			}
			return sb.toString();
		}

		public Mapper<String> makeMapper() {
			return CharacterStringMapper.getDefault();
		}

		@Override
		public String getTypeName() {
			return "string";
		}

	}

	private class IntegerPropertyGenerator extends PropertyGenerator<Integer> {

		public IntegerPropertyGenerator(String name, Random random, int min, int max) {
			super(name, random, min, max);
		}

		public Integer next() {
			return min + random.nextInt(max - min);
		}

		public Mapper<Integer> makeMapper() {
			return IntegerMapper.withRangeAndCategories(min, max, 100);
		}

		@Override
		public String getTypeName() {
			return "integer";
		}

	}

	private class FloatPropertyGenerator extends PropertyGenerator<Float> {

		public FloatPropertyGenerator(String name, Random random, float min, float max) {
			super(name, random, min, max);
		}

		public Float next() {
			return min + (max - min) * random.nextFloat();
		}

		public Mapper<Float> makeMapper() {
			return FloatMapper.withRangeAndCategories(min, max, 100);
		}

		@Override
		public String getTypeName() {
			return "float";
		}

	}

	@Test
	public void testInsertSimple1D_10() throws Exception {
		doInsertSimple1D(0, 10, 1);
	}

	@Test
	public void testInsertSimple1D_100() throws Exception {
		doInsertSimple1D(0, 100, 5);
	}

	private void doInsertSimple1D(int min, int max, int step) throws Exception {
		int average = (max + min) / 2;
		final ArrayList<PropertyConfig<?>> properties = new ArrayList<PropertyConfig<?>>();
		properties.add(DefaultPropertyConfig.makeIntegerConfig("simple", min, max));
		assertEquals("Expected first property to have origin " + average, average, properties.get(0).getMapper().getOrigin());
		Transaction tx = graphDb().beginTx();
		try {
			IndexConfig config = new DefaultIndexConfig(10, properties);
			AmanziIndex index = new AmanziIndex("Test Index", graphDb(), config);
			System.out.println("Creating simple set of ordered integers:");
			for (int i = min; i <= max; i += step) {
				Node node = this.graphDb().createNode();
				node.setProperty("simple", i);
				index.add(node);
				int[] keys = (int[]) node.getSingleRelationship(AmanziIndexRelationshipTypes.INDEX_LEAF, Direction.INCOMING)
						.getStartNode().getProperty("index");
				String keyString = arrayString(keys);
				System.out.println("\t" + i + " -> " + arrayString(keys));
				assertEquals("Expected index key to be the value " + i + " minus the average value " + average, "[" + (i - average)
						+ "]", keyString);
			}
			index.finishUp();
			tx.success();
		} finally {
			tx.finish();
		}
		assertEquals("Expected first property to have config max " + max, max, properties.get(0).getMax());
		assertEquals("Expected first property to have mapper max " + max, max, properties.get(0).getMapper().getMax());
		debugIndex("Test Index 1D Integer[" + min + "," + max + "," + step + "]");
	}

	@Test
	public void testInsertSimple1DString_1() throws Exception {
		doInsertSimple1DString("A", "B", 1, 1);
	}

	@Test
	public void testInsertSimple1DString_26() throws Exception {
		doInsertSimple1DString("A", "Z", 1, 1);
	}

	@Test
	public void testInsertSimple1DString_2x13() throws Exception {
		doInsertSimple1DString("A", "Z", 13, 2);
	}

	private void doInsertSimple1DString(String min, String max, int step, int depth) throws Exception {
		int minc = min.charAt(0);
		int maxc = max.charAt(0);
		int average = (maxc + minc) / 2;
		final ArrayList<PropertyConfig<?>> properties = new ArrayList<PropertyConfig<?>>();
		properties.add(DefaultPropertyConfig.makeStringConfig("simple", min, max, depth));
		assertEquals("Expected first property to have origin " + average, average,
				((String) (properties.get(0).getMapper().getOrigin())).charAt(0));
		Transaction tx = graphDb().beginTx();
		try {
			IndexConfig config = new DefaultIndexConfig(10, properties);
			AmanziIndex index = new AmanziIndex("Test Index", graphDb(), config);
			System.out.println("Creating simple set of ordered integers:");
			for (int i = minc, p = minc; i <= maxc; i += step, p += step) {
				char[] value = Arrays.copyOf(((String) (properties.get(0).getMapper().getOrigin())).toCharArray(), depth);
				for (int d = 0; d < depth; d++) {
					if (p > maxc)
						p = minc;
					value[d] = (char) p;
					Node node = this.graphDb().createNode();
					node.setProperty("simple", String.valueOf(value));
					index.add(node);
					int[] keys = (int[]) node.getSingleRelationship(AmanziIndexRelationshipTypes.INDEX_LEAF, Direction.INCOMING)
							.getStartNode().getProperty("index");
					String keyString = arrayString(keys);
					System.out.println("\t" + p + " -> " + arrayString(keys));
					if (d == 0) {
						assertEquals("Expected index key to be the value " + p + " minus the average value " + average, "["
								+ (p - average) * (int)Math.pow(94, depth - 1) + "]", keyString);
					}
				}
			}
			index.finishUp();
			tx.success();
		} finally {
			tx.finish();
		}
		assertEquals("Expected first property to have config max " + max, max, properties.get(0).getMax());
		assertEquals("Expected first property to have mapper max " + max, max, properties.get(0).getMapper().getMax());
		debugIndex("Test Index 1D String[" + min + "," + max + "," + step + "," + depth + "]");
	}

	@Test
	public void testInsert2D() throws Exception {
		final ArrayList<PropertyConfig<?>> properties = new ArrayList<PropertyConfig<?>>();
		properties.add(new StringPropertyGenerator("name", new Random(0), "A", "Z"));
		properties.add(new IntegerPropertyGenerator("an integer value", new Random(0), -150, -15));
		properties.add(new FloatPropertyGenerator("a float value", new Random(), 0.001f, 100));
		assertEquals("Expected first property to be 'name'", "name", properties.get(0).getName());
		Transaction tx = graphDb().beginTx();
		try {
			IndexConfig config = new DefaultIndexConfig(10, properties);
			AmanziIndex index = new AmanziIndex("Test Index", graphDb(), config);
			for (int i = 0; i < 10; i++) {
				Node node = this.graphDb().createNode();
				for (PropertyConfig<?> property : properties) {
					PropertyGenerator<?> gen = (PropertyGenerator<?>) property;
					Object value = gen.next();
					if (value != null) {
						node.setProperty(property.getName(), value);
					}
				}
				index.add(node);
			}
			index.finishUp();
			tx.success();
		} finally {
			tx.finish();
		}
		debugIndex("Test Index");
	}

	private void debugIndex(String indexName) {
		AmanziIndex index = new AmanziIndex(indexName, graphDb());
		index.debug();
	}

	@Test
	public void testHash() {
		HashSet<int[]> data = new HashSet<int[]>();
		for (int i = 0; i < 10; i++) {
			int test[] = new int[5];
			for (int p = 0; p < 5; p++) {
				test[p] = p * i;
			}
			System.out.println("Created test with hashcode=" + test.hashCode() + ": " + arrayString(test));
			data.add(test);
		}
		for (int[] test : data) {
			System.out.println("Read test with hashcode=" + test.hashCode() + ": " + arrayString(test));
		}
	}

}
