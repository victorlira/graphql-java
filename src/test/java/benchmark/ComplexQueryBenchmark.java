package benchmark;

import com.google.common.collect.ImmutableList;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;

/**
 * This benchmark is an attempt to have a more complex query that involves async and sync work together
 * along with multiple threads happening.
 * <p>
 * It can also be run in a forever mode say if you want to connect a profiler to it say
 */
@State(Scope.Benchmark)
@Warmup(iterations = 2, time = 5)
@Measurement(iterations = 2)
@Fork(1)
public class ComplexQueryBenchmark {

    @Param({"5", "10", "20"})
    int howManyThreads = 5;
    @Param({"10", "50", "100"})
    int howManyQueries = 10;
    @Param({"5", "20", "100"})
    int howManyItems = 5;
    @Param({"1", "5", "10"})
    int howLongToSleep = 1;

    ExecutorService executorService;
    GraphQL graphQL;
    volatile boolean shutDown;

    @Setup(Level.Trial)
    public void setUp() {
        shutDown = false;
        executorService = Executors.newFixedThreadPool(howManyThreads);
        graphQL = buildGraphQL();
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        shutDown = true;
        executorService.shutdownNow();
    }


    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public Object benchMarkSimpleQueriesThroughput() {
        return runManyQueriesToCompletion();
    }


    public static void main(String[] args) throws Exception {
        // just to make sure it's all valid before testing
        runAtStartup();

        Options opt = new OptionsBuilder()
                .include("benchmark.ComplexQueryBenchmark")
                .addProfiler(GCProfiler.class)
                .build();

        new Runner(opt).run();
    }

    @SuppressWarnings({"ConstantValue", "LoopConditionNotUpdatedInsideLoop"})
    private static void runAtStartup() {
        // set this to true if you want to hook in profiler say to a forever running JVM
        boolean forever = false;

        long then = System.currentTimeMillis();
        System.out.printf("Running initial code before starting the benchmark in forever=%b mode \n", forever);
        ComplexQueryBenchmark complexQueryBenchmark = new ComplexQueryBenchmark();
        complexQueryBenchmark.setUp();
        do {
            complexQueryBenchmark.runManyQueriesToCompletion();
        } while (forever);
        complexQueryBenchmark.tearDown();

        System.out.printf("This took %d millis\n", System.currentTimeMillis() - then);

    }

    @SuppressWarnings("UnnecessaryLocalVariable")
    private Void runManyQueriesToCompletion() {
        CompletableFuture<?>[] cfs = new CompletableFuture[howManyQueries];
        for (int i = 0; i < howManyQueries; i++) {
            cfs[i] = CompletableFuture.supplyAsync(() -> executeQuery(howManyItems, howLongToSleep), executorService);
        }
        Void result = CompletableFuture.allOf(cfs).join();
        return result;
    }

    @SuppressWarnings("UnnecessaryLocalVariable")
    public ExecutionResult executeQuery(int howMany, int howLong) {
        String fields = "id name f1 f2 f3 f4 f5 f6 f7 f8 f9 f10";
        String query = "query q {"
                + String.format("shops(howMany : %d) { %s departments( howMany : %d) { %s products(howMany : %d) { %s }}}\n"
                , howMany, fields, howMany, fields, howMany, fields)
                + String.format("expensiveShops(howMany : %d howLong : %d) { %s expensiveDepartments( howMany : %d howLong : %d) { %s expensiveProducts(howMany : %d howLong : %d) { %s }}}\n"
                , howMany, howLong, fields, howMany, howLong, fields, howMany, howLong, fields)
                + "}";
        ExecutionResult executionResult = graphQL.execute(query);
        return executionResult;
    }

    private GraphQL buildGraphQL() {
        TypeDefinitionRegistry definitionRegistry = new SchemaParser().parse(BenchmarkUtils.loadResource("storesanddepartments.graphqls"));

        DataFetcher<?> shopsDF = env -> mkHowManyThings(env.getArgument("howMany"));
        DataFetcher<?> expensiveShopsDF = env -> supplyAsync(() -> sleepAndReturnThings(env));
        DataFetcher<?> departmentsDF = env -> mkHowManyThings(env.getArgument("howMany"));
        DataFetcher<?> expensiveDepartmentsDF = env -> supplyAsync(() -> sleepAndReturnThings(env));
        DataFetcher<?> productsDF = env -> mkHowManyThings(env.getArgument("howMany"));
        DataFetcher<?> expensiveProductsDF = env -> supplyAsync(() -> sleepAndReturnThings(env));

        RuntimeWiring runtimeWiring = RuntimeWiring.newRuntimeWiring()
                .type(newTypeWiring("Query")
                        .dataFetcher("shops", shopsDF)
                        .dataFetcher("expensiveShops", expensiveShopsDF))
                .type(newTypeWiring("Shop")
                        .dataFetcher("departments", departmentsDF)
                        .dataFetcher("expensiveDepartments", expensiveDepartmentsDF))
                .type(newTypeWiring("Department")
                        .dataFetcher("products", productsDF)
                        .dataFetcher("expensiveProducts", expensiveProductsDF))
                .build();

        GraphQLSchema graphQLSchema = new SchemaGenerator().makeExecutableSchema(definitionRegistry, runtimeWiring);

        return GraphQL.newGraphQL(graphQLSchema).build();
    }

    private <T> CompletableFuture<T> supplyAsync(Supplier<T> codeToRun) {
        if (!shutDown) {
            return CompletableFuture.supplyAsync(codeToRun);
        } else {
            // if we have shutdown - get on with it, so we shut down quicker
            return CompletableFuture.completedFuture(codeToRun.get());
        }
    }

    private List<IdAndNamedThing> sleepAndReturnThings(DataFetchingEnvironment env) {
        // by sleeping, we hope to cause the objects to stay longer in GC land and hence have a longer lifecycle
        // then a simple stack say or young gen gc.  I don't know this will work, but I am trying it
        // to represent work that takes some tie to complete
        sleep(env.getArgument("howLong"));
        return mkHowManyThings(env.getArgument("howMany"));
    }

    private void sleep(Integer howLong) {
        if (howLong > 0) {
            try {
                Thread.sleep(howLong);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private List<IdAndNamedThing> mkHowManyThings(Integer howMany) {
        ImmutableList.Builder<IdAndNamedThing> builder = ImmutableList.builder();
        for (int i = 0; i < howMany; i++) {
            builder.add(new IdAndNamedThing(i));
        }
        return builder.build();
    }

    @SuppressWarnings("unused")
    static class IdAndNamedThing {
        private final int i;

        public IdAndNamedThing(int i) {
            this.i = i;
        }

        public String getId() {
            return "id" + i;
        }

        public String getName() {
            return "name" + i;
        }

        public String getF1() {
            return "f1" + i;
        }

        public String getF2() {
            return "f2" + i;
        }

        public String getF3() {
            return "f3" + i;
        }

        public String getF4() {
            return "f4" + i;
        }

        public String getF5() {
            return "f5" + i;
        }

        public String getF6() {
            return "f6" + i;
        }

        public String getF7() {
            return "f7" + i;
        }

        public String getF8() {
            return "f8" + i;
        }

        public String getF9() {
            return "f9" + i;
        }

        public String getF10() {
            return "f10" + i;
        }
    }
}
