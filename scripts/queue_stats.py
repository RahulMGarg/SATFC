# coding: utf-8
import redis
import argparse

# Load up a redis queue with jobs

parser = argparse.ArgumentParser()
parser.add_argument('--qname', type=str, help="redis queue to delete")
parser.add_argument('--host', type=str, help="redis host")
parser.add_argument('--port', type=int, help="redis port")
args = parser.parse_args()

r = redis.StrictRedis(host=args.host, port=args.port)
remaining_jobs = r.llen(args.qname)
processing_jobs = r.llen(args.qname+'_PROCESSING')
timeouts = r.llen(args.qname+'_TIMEOUTS')
print "There are %d jobs in the queue" % remaining_jobs
print "There are %d jobs in the processing queue" % processing_jobs
print "There are %d timeouts" % timeouts
