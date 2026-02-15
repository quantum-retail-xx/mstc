import java.util.*;
import java.util.stream.Stream;

public class test {

    public static void main(String[] args){

Person p = new Person();
p.setAge(1);

        Person p2 = new Person();
        p.setAge(2);

        Person p3 = new Person();
        p.setAge(3);

        List<Person> persons= Arrays.asList(p,p2,p3);
        persons.stream().sorted();

        String name="aab";
        Map<Character,Integer> map = new HashMap<>();
        for(int i = 0;i<name.length();i++){
         Integer count=   map.get(new Character(name.charAt(i)));
         if(count==null){
             count=new Integer(0);
         }


            map.put(new Character(name.charAt(i)),++count);

        }

System.out.println(map.entrySet().toString());

    }



   static class Person{

        private int age;

        public int getAge() {
            return age;
        }

        public void setAge(int age) {
            this.age = age;
        }
    }




}
