cd client
mvn clean package install

cd ../database
mvn clean package install

cd ../www
mvn clean package 

cd ../bridge
mvn clean package 

cd ../master
mvn clean package 


cd ../provider
mvn clean package 

cd ..
echo "DONE"
