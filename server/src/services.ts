import { PrismaClient } from "@prisma/client";
import { Queue } from "bullmq";
import { config } from "./config.js";

export const prisma = new PrismaClient();
export const downloadsQueue = new Queue("media-downloads", { connection: { url: config.REDIS_URL } });
