package cmpt;

import java.util.ArrayList;
import java.util.Iterator;

import org.apache.accumulo.core.client.ConditionalWriter;
import org.apache.accumulo.core.client.ConditionalWriter.Result;
import org.apache.accumulo.core.client.ConditionalWriter.Status;
import org.apache.accumulo.core.client.ConditionalWriterConfig;
import org.apache.accumulo.core.client.Connector;
import org.apache.accumulo.core.client.TableNotFoundException;
import org.apache.accumulo.core.client.ZooKeeperInstance;
import org.apache.accumulo.core.client.security.tokens.PasswordToken;
import org.apache.accumulo.core.conf.Property;
import org.apache.accumulo.core.data.Condition;
import org.apache.accumulo.core.data.ConditionalMutation;
import org.apache.commons.configuration.PropertiesConfiguration;

public class Test1 {

  public static void runPerfTest(Connector conn, String tableName) throws Exception {

    try{
      conn.tableOperations().delete(tableName);
    } catch(TableNotFoundException e){}

    conn.tableOperations().create(tableName);
    conn.tableOperations().setProperty(tableName,Property.TABLE_BLOCKCACHE_ENABLED.getKey(), "true");


    ConditionalWriter cw = conn.createConditionalWriter(tableName, new ConditionalWriterConfig());

    timeX(cw, null);

    double rateSum = 0;

    for(int i = 1; i< 20; i++) {
      rateSum+=timeX(cw, (long)i);
    }

    System.out.printf("rate avg : %6.2f conditionalMutations/sec \n", rateSum/20);

    System.out.println("Flushing");
    conn.tableOperations().flush(tableName, null, null, true);

    rateSum = 0;

    for(int i = 20; i< 40; i++) {
      rateSum += timeX(cw, (long)i);
    }

    System.out.printf("rate avg : %6.2f conditionalMutations/sec \n", rateSum/20);
  }

  private static double timeX(ConditionalWriter cw, Long seq) throws Exception {
    ArrayList<ConditionalMutation> cmuts = new ArrayList<>();

    for(int i = 0; i < 10000; i++) {
      Condition cond = new Condition("meta", "seq");
      if(seq != null) {
        cond.setValue(""+seq);
      }

      ConditionalMutation cm = new ConditionalMutation(String.format("r%07d", i), cond);
      cm.put("meta", "seq", seq == null ? "1" : (seq +1)+"");
      cmuts.add(cm);
    }


    long t1 = System.currentTimeMillis();

    int count = 0;
    Iterator<Result> results = cw.write(cmuts.iterator());
    while(results.hasNext()) {
      Result result = results.next();

      if(Status.ACCEPTED != result.getStatus()) {throw new RuntimeException();}
      count++;
    }

    if(cmuts.size() != count) {throw new RuntimeException();}
    long t2 = System.currentTimeMillis();


    double rate = 10000 / ((t2 -t1)/1000.0);
    System.out.printf("time: %d ms  rate : %6.2f conditionalMutations/sec \n", (t2 - t1), rate);
    return rate;
  }

  public static void main(String[] args) throws Exception  {
    PropertiesConfiguration config = new PropertiesConfiguration(args[0]);
    ZooKeeperInstance zki = new ZooKeeperInstance(config);
    Connector conn = zki.getConnector(config.getString("user.name"), new PasswordToken(config.getString("user.password")));
    runPerfTest(conn, "foo");
  }
}
