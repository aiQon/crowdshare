#include <stdio.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <sys/types.h>
#include <unistd.h>
#include <string.h>
#include <linux/netfilter.h>
#include <linux/ip.h>
#include <netinet/tcp.h>
#include <libipq/libipq.h>
#include <stdio.h>
#include <stdlib.h>
#include <pthread.h>
#include <stdbool.h>

#define BUFFSIZE 4096
#define IPCOMP(addr, n) ((addr >> (24 - 8 * n)) & 0xFF)
#define TIMEVALIDITY 600
#define CLEANER_INTERVAL 300


struct history_element {
	uint32_t ip;
	uint16_t port;
	time_t timestamp;
	struct history_element *next;
};

struct history_element *head = NULL;
struct history_element *curr = NULL;



int running = 1;
pthread_mutex_t lock;

pthread_t sender;
pthread_t reader;
pthread_t cleaner;

int delete_from_list_element(struct history_element *del, struct history_element *prev){
	if(del == NULL){
		return -1;
	} else {
		if(prev != NULL){
			//printf("prev != NULL\n");
			prev->next = del->next;
		}

	    if(del == curr){
	    	//printf("del == curr, means last element\n");
	    	curr = prev = head = NULL;
	    } else if(del == head) {
	    	head = del->next;
	    	//printf("deleted head element");
	    }
	}
	free(del);
	del = NULL;
	return 0;
}

struct history_element* search_in_list(uint32_t ip, uint16_t port, struct history_element **prev)
{
	pthread_mutex_lock(&lock);
	time_t current_time;
	time(&current_time);
    struct history_element *ptr = head;
    struct history_element *tmp = NULL;
    bool found = false;

    //printf("\n Searching the list for value [%d:%d] \n",ip,port);

    while(ptr != NULL){
        if(ptr->ip == ip && ptr->port == port && ptr->timestamp + TIMEVALIDITY > current_time){
            found = true;
            break;
        } else {
            tmp = ptr;
            ptr = ptr->next;
        }
    }

    pthread_mutex_unlock(&lock);

    if(true == found){
        if(prev)
            *prev = tmp;
        return ptr;
    } else {
        return NULL;
    }
}


int delete_from_list(uint32_t ip, uint16_t port)
{
    struct history_element *prev = NULL;
    struct history_element *del = NULL;
    //printf("\n Deleting value [%d:%d] from list\n",ip,port);
    del = search_in_list(ip,port,&prev);
    return delete_from_list_element(del, prev);
}


struct history_element* create_list(uint32_t ip, uint16_t port, uint64_t timestamp)
{
    //printf("\n creating list with headnode as [%d]\n",ip);
    struct history_element *ptr = (struct history_element*)malloc(sizeof(struct history_element));
    if(NULL == ptr)
    {
        return NULL;
    }
    ptr->ip = ip;
    ptr->port = port;
    ptr->timestamp = timestamp;
    ptr->next = NULL;

    head = curr = ptr;
    return ptr;
}


struct history_element* add_to_list(uint32_t ip, uint16_t port, uint64_t timestamp)
{
    if(NULL == head)
    {
        return (create_list(ip,port,timestamp));
    }
    struct history_element *ptr = (struct history_element*)malloc(sizeof(struct history_element));
    if(NULL == ptr){
        return NULL;
    }
    ptr->ip = ip;
    ptr->port = port;
    ptr->timestamp = timestamp;
    ptr->next = NULL;

    pthread_mutex_lock(&lock);
    curr->next = ptr;
    curr = ptr;
    pthread_mutex_unlock(&lock);
    return ptr;
}


/*
 * Checks if the given history_element is outdated and deletes if necessary. On deletion
 * returns 0, on error -1 and on verifying an entry to be valid it returns 1;
 */
//int delete_if_invalid(struct history_element *ptr, time_t current_time){
//	if(ptr->timestamp + TIMEVALIDITY < current_time){
//		return delete_from_list_element(ptr);
//	} else {
//		return 1;
//	}
//}

/*
void printlist(){
	struct history_element *ptr = head;
	//printf("printing list\n");
	while(ptr != NULL){
		printf("element:%d\n", ptr->ip);
		ptr = ptr->next;
	}
	//printf("printing list done");
}
*/

void clean_list(){
	while(running){
		sleep(CLEANER_INTERVAL);
		//printlist();
		//printf("cleaning list\n");
		struct history_element *ptr = head;
		struct history_element *prev = NULL;
		time_t current_time;
		time(&current_time);

		//printf("waiting for the lock\n");
		pthread_mutex_lock(&lock);
		//printf("got the lock\n");
		while(ptr != NULL){
			//printf("checking on:%d\n", ptr->ip);
			if(ptr->timestamp + TIMEVALIDITY < current_time){
				//printf("need to delete it\n");
				delete_from_list_element(ptr, prev);
				//printf("element deleted\n");
				if(prev != NULL){
					//printf("was not first element in list, proceeding\n");
					prev = ptr;
					ptr = ptr->next;
				}
				else{
					//printf("was first element in list\n");
					ptr = head;
				}
			} else {
				//printf("did not delete anything, proceeding to next element\n");
				prev = ptr;
				ptr = ptr->next;
			}
		}
		//printf("cleaning done\n");
		pthread_mutex_unlock(&lock);
		//printf("releasing lock\n");
	}
}

//void search_and_add_if_needed(uint32_t ip, uint16_t port){
//	struct history_element *prev = NULL;
//	if(NULL == search_in_list(ip,port,&prev)){
//		add_to_list(ip,port,current_time);
//	}
//}





struct send_thread_param{
		struct ipq_handle *h;
		int *connection_fd;
};

void stop(){
	running = 0;
}

static void die(struct ipq_handle *h){
	stop();
	ipq_perror("passer");
	ipq_destroy_handle(h);
	exit(1);
}

void *read_java(void *arg){
	struct send_thread_param *params = (struct send_thread_param *) arg;
	struct ipq_handle* h = params->h;
	int *connection_fd = params->connection_fd;
	int nbytes;
	char buffer[BUFFSIZE];
	int status;
	char *search = ";";
	char *id;
	char *ip;
	char *port;
	time_t current_time;
	do{
		nbytes = read(*connection_fd, buffer, BUFFSIZE);
		if(nbytes > 0){
			//printf("got from java:%s\n", buffer);
			id = strtok(buffer, search);
			ip = strtok(NULL, search);
			port = strtok(NULL, search);
			time(&current_time);
			unsigned long packet_id = strtoul(id, NULL, 10);
			//printf("ip before conversion as string:>%s<\n", ip);
			//printf("port before conversion as string:>%s<\n", port);

			uint32_t ip_int = atoi(ip);
			uint16_t port_int = atoi(port);
			//printf("got from java extracted:%lu=%d:%d\n", packet_id,ip_int,port_int);
			add_to_list(ip_int, port_int, current_time);
			status = ipq_set_verdict(h, packet_id, NF_ACCEPT, 0, NULL);
			if (status < 0)
				die(h);
		}
	}while(running);
	free(arg);
}

void *send_ipq(void *arg){
	struct send_thread_param *params = (struct send_thread_param *) arg;
	struct ipq_handle* h = params->h;
	int *connection_fd = params->connection_fd;
	int needed_chars = 1024;
	char response[needed_chars];
	int used_chars;
	unsigned char buf[BUFFSIZE];
	int status;
	bool need_write = false;

	do {
		status = ipq_read(h, buf, BUFFSIZE, 0);
		if (status < 0) {
			die(h);
		}

		switch (ipq_message_type(buf)) {
		case NLMSG_ERROR:
			used_chars = snprintf(response, needed_chars,
					"Received error message %d\n", ipq_get_msgerr(buf));
			need_write = true;
			break;

		case IPQM_PACKET: {
			ipq_packet_msg_t *m = ipq_get_packet(buf);
			struct iphdr *ip = (struct iphdr*) m->payload;
			int protocol = (int) ip->protocol;
			struct tcphdr *tcp = (struct tcphdr*) (m->payload + (4 * ip->ihl));
			int source_ip = ip->saddr;
			int destination_ip = ip->daddr;
			int port = htons(tcp->dest);
			struct history_element *prev;

			//printf("searching for socket=%d:%d\n", destination_ip, port);

			if(search_in_list(destination_ip, port, &prev) == NULL){
				used_chars = snprintf(response, needed_chars,
									"%lu=%d.%d.%d.%d>%d.%d.%d.%d:%d,%d\n", m->packet_id,
									IPCOMP(source_ip, 3), IPCOMP(source_ip, 2),
									IPCOMP(source_ip, 1), IPCOMP(source_ip, 0),
									IPCOMP(destination_ip, 3), IPCOMP(destination_ip, 2),
									IPCOMP(destination_ip, 1), IPCOMP(destination_ip, 0),
									port, protocol);
				//printf("could not find entry for searched socket:%s\n", response);
				need_write = true;
			}else{
				//printf("found entry for socket %d:%d\n", destination_ip, port);
				status = ipq_set_verdict(h, m->packet_id, NF_ACCEPT, 0, NULL);
				need_write = false;
				if (status < 0)
					die(h);
			}
			break;
		}

		default:
			used_chars = snprintf(response, needed_chars,
					"Unknown message type!\n");
			need_write = true;
			break;
		}
		if(need_write && write(*connection_fd, response, used_chars) != used_chars){
			//printf("wrote response to socket:%s\n", response);
			stop();
		}
	} while (running);
	free(arg);
}

struct ipq_handle* init_ipq(int status, struct ipq_handle* h) {
	//printf("[*] creating handle with %d\n", NFPROTO_IPV4);
	h = ipq_create_handle(0, NFPROTO_IPV4);
	if (!h) {
		//printf("cant create handle\n");
		die(h);
	}
	//printf("[*] setting mode to %d\n", IPQ_COPY_PACKET);IPQ_COPY_META
	/* Set the SO_RCVBUF Size:*/
	int rcvbuf = 112640; //sysctl net.core.wmem_max
	status = setsockopt(h->fd,SOL_SOCKET,SO_RCVBUF,&rcvbuf,sizeof (rcvbuf));
	if(status){
		//printf("failed to increase rcvbuf size\n");
		die(h);
	}

	status = ipq_set_mode(h, IPQ_COPY_PACKET, BUFFSIZE);
	//status = ipq_set_mode(h, IPQ_COPY_META, BUFFSIZE);
	if (status < 0) {
		die(h);
	}
	return h;
}

start_cleaner_thread() {
	int err = pthread_create(&(cleaner), NULL, &clean_list, (void*) NULL);
	if (err != 0) {
		//printf("\ncan't create cleaner thread:[%s]", strerror(err));
		stop();
	}
}


start_reader_thread(int connection_fd, struct ipq_handle* h) {
	struct send_thread_param* tp;
	if ((tp = malloc(sizeof(*tp))) == NULL) {
		fprintf(stderr, "MALLOC THREAD_PARAM ERROR");
		die(h);
	}
	tp->h = h;
	tp->connection_fd = &connection_fd;
	int err = pthread_create(&(reader), NULL, &read_java, (void*) tp);
	if (err != 0) {
		//printf("\ncan't create reader thread:[%s]", strerror(err));
		stop();
	}
}

start_sender_thread(int connection_fd, struct ipq_handle* h) {
	struct send_thread_param* tp;
	if ((tp = malloc(sizeof(*tp))) == NULL) {
		fprintf(stderr, "MALLOC THREAD_PARAM ERROR");
		die(h);
	}
	tp->h = h;
	tp->connection_fd = &connection_fd;
	int err = pthread_create(&(sender), NULL, &send_ipq, (void*) tp);
	if (err != 0) {
		//printf("\ncan't create sender thread:[%s]", strerror(err));
		stop();
	}
}

int connection_handler(int connection_fd){
	int status;
	struct ipq_handle *h;

	if (pthread_mutex_init(&lock, NULL) != 0){
		//printf("\n mutex init failed\n");
		return 1;
	}


	h = init_ipq(status, h);

	start_sender_thread(connection_fd, h);
	start_reader_thread(connection_fd, h);
	start_cleaner_thread();

	pthread_join(sender, NULL);
	pthread_join(reader, NULL);
	pthread_join(cleaner, NULL);
	pthread_mutex_destroy(&lock);
	ipq_destroy_handle(h);
	close(connection_fd);
	return 0;
}

int main(int argc, char **argv)
{
 struct sockaddr_un address;
 int socket_fd, connection_fd;
 socklen_t address_length;
 pid_t child;

 socket_fd = socket(PF_UNIX, SOCK_STREAM, 0);

 if(argc != 2){
	 printf("call with the unix domain socket destination as single argument.\n");
	 return 1;
 }


 if(socket_fd < 0)
 {
  //printf("socket() failed\n");
  return 1;
 }

 unlink(argv[1]);

 /* start with a clean address structure */
 memset(&address, 0, sizeof(struct sockaddr_un));

 address.sun_family = AF_UNIX;
 snprintf(address.sun_path, UNIX_PATH_MAX, argv[1]);

 if(bind(socket_fd,
         (struct sockaddr *) &address,
         sizeof(struct sockaddr_un)) != 0)
 {
  //printf("bind() failed on %s\n", argv[1]);
  return 1;
 }

 if(listen(socket_fd, 5) != 0)
 {
  //printf("listen() failed\n");
  return 1;
 }

 while((connection_fd = accept(socket_fd,
                               (struct sockaddr *) &address,
                               &address_length)) > -1)
 {
  printf("[*] client connected\n");
  child = fork();
  if(child == 0)
  {
   /* now inside newly created connection handling process */
   return connection_handler(connection_fd);
  }

  /* still inside server process */
  close(connection_fd);
 }

 close(socket_fd);
 unlink(argv[1]);
 return 0;
}
