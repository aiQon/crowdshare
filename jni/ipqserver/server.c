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

#define BUFFSIZE 4096
#define IPCOMP(addr, n) ((addr >> (24 - 8 * n)) & 0xFF)

int running = 1;
pthread_mutex_t lock;

pthread_t sender;
pthread_t reader;

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
	printf("[*] reader thread online\n");
	do{
		nbytes = read(*connection_fd, buffer, BUFFSIZE);
		if(nbytes > 0){
			printf("got from java:%s\n", buffer);
			unsigned long packet_id = strtoul(buffer, NULL, 10);
			printf("got from java extracted:%lu\n", packet_id);
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
	printf("[*] sender thread online\n");

	do {

		status = ipq_read(h, buf, BUFFSIZE, 0);
		if (status < 0) {
			die(h);
		}

		switch (ipq_message_type(buf)) {
		case NLMSG_ERROR:
			used_chars = snprintf(response, needed_chars,
					"Received error message %d\n", ipq_get_msgerr(buf));
			break;

		case IPQM_PACKET: {
			ipq_packet_msg_t *m = ipq_get_packet(buf);
			struct iphdr *ip = (struct iphdr*) m->payload;
			int protocol = (int) ip->protocol;
			struct tcphdr *tcp = (struct tcphdr*) (m->payload + (4 * ip->ihl));
			int source_ip = ip->saddr;
			int destination_ip = ip->daddr;
			int port = htons(tcp->dest);
			used_chars = snprintf(response, needed_chars,
					"%lu=%d.%d.%d.%d>%d.%d.%d.%d:%d,%d\n", m->packet_id,
					IPCOMP(source_ip, 3), IPCOMP(source_ip, 2),
					IPCOMP(source_ip, 1), IPCOMP(source_ip, 0),
					IPCOMP(destination_ip, 3), IPCOMP(destination_ip, 2),
					IPCOMP(destination_ip, 1), IPCOMP(destination_ip, 0),
					port, protocol);
			printf(response);
			//status = ipq_set_verdict(h, m->packet_id, NF_ACCEPT, 0, NULL);
			//if (status < 0)
			//	die(h);
			break;
		}

		default:
			used_chars = snprintf(response, needed_chars,
					"Unknown message type!\n");
			break;
		}
		if(write(*connection_fd, response, used_chars) != used_chars){
			stop();
		}
	} while (running);
	free(arg);
}

struct ipq_handle* init_ipq(int status, struct ipq_handle* h) {
	//printf("[*] creating handle with %d\n", NFPROTO_IPV4);
	h = ipq_create_handle(0, NFPROTO_IPV4);
	if (!h) {
		printf("cant create handle\n");
		die(h);
	}
	//printf("[*] setting mode to %d\n", IPQ_COPY_PACKET);IPQ_COPY_META
	/* Set the SO_RCVBUF Size:*/
	int rcvbuf = 112640; //sysctl net.core.wmem_max
	status = setsockopt(h->fd,SOL_SOCKET,SO_RCVBUF,&rcvbuf,sizeof (rcvbuf));
	if(status){
		printf("failed to increase rcvbuf size\n");
		die(h);
	}

	status = ipq_set_mode(h, IPQ_COPY_PACKET, BUFFSIZE);
	//status = ipq_set_mode(h, IPQ_COPY_META, BUFFSIZE);
	if (status < 0) {
		die(h);
	}
	return h;
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
		printf("\ncan't create reader thread:[%s]", strerror(err));
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
		printf("\ncan't create sender thread:[%s]", strerror(err));
		stop();
	}
}

int connection_handler(int connection_fd){
	int status;
	struct ipq_handle *h;

	if (pthread_mutex_init(&lock, NULL) != 0){
		printf("\n mutex init failed\n");
		return 1;
	}


	h = init_ipq(status, h);

	start_sender_thread(connection_fd, h);
	start_reader_thread(connection_fd, h);

	pthread_join(sender, NULL);
	pthread_join(reader, NULL);
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
  printf("socket() failed\n");
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
  printf("bind() failed on %s\n", argv[1]);
  return 1;
 }

 if(listen(socket_fd, 5) != 0)
 {
  printf("listen() failed\n");
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
