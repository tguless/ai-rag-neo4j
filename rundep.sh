cd ..
# Check if the directory does not exist
if [ ! -d "neo4j-gtfs" ]; then
    # Clone the repository if the directory does not exist
    git clone https://github.com/tguless/neo4j-gtfs.git
fi
cd ./neo4j-gtfs/complete
mvn install
docker-compose build neo4j-gtfs-java
docker-compose up

